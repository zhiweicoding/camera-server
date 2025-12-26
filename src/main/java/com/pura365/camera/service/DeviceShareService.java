package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.domain.DeviceShare;
import com.pura365.camera.domain.User;
import com.pura365.camera.domain.UserDevice;
import com.pura365.camera.repository.DeviceShareRepository;
import com.pura365.camera.repository.UserDeviceRepository;
import com.pura365.camera.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 设备分享服务
 */
@Service
public class DeviceShareService {

    private static final Logger log = LoggerFactory.getLogger(DeviceShareService.class);

    /** 分享码有效期（小时） */
    private static final int SHARE_CODE_EXPIRE_HOURS = 24;

    @Autowired
    private DeviceShareRepository deviceShareRepository;

    @Autowired
    private UserDeviceRepository userDeviceRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * 检查用户是否是设备的拥有者
     */
    public boolean isDeviceOwner(Long userId, String deviceId) {
        QueryWrapper<UserDevice> qw = new QueryWrapper<>();
        qw.lambda().eq(UserDevice::getUserId, userId)
                .eq(UserDevice::getDeviceId, deviceId)
                .eq(UserDevice::getRole, "owner");
        return userDeviceRepository.selectCount(qw) > 0;
    }

/**
     * 生成分享码
     * @param ownerUserId 设备拥有者ID
     * @param deviceId 设备ID
     * @param permission 权限：view_only 或 full_control
     * @param targetAccount 分享目标账号（可为空）：账号/邮箱/手机号
     * @return 分享信息
     */
    public Map<String, Object> generateShareCode(Long ownerUserId, String deviceId, String permission, String targetAccount) {
        // 验证权限参数
        if (!"view_only".equals(permission) && !"full_control".equals(permission)) {
            permission = "view_only"; // 默认仅查看
        }

        User targetUser = null;
        // 如果指定了分享目标账号，则先查出对应用户，限定分享对象
        if (targetAccount != null && !targetAccount.trim().isEmpty()) {
            String acc = targetAccount.trim();

            QueryWrapper<User> uq = new QueryWrapper<>();
            uq.lambda()
                    .eq(User::getUsername, acc)
                    .or().eq(User::getPhone, acc)
                    .or().eq(User::getEmail, acc);

            targetUser = userRepository.selectOne(uq);
            if (targetUser == null) {
                throw new RuntimeException("目标用户不存在");
            }
            if (targetUser.getId().equals(ownerUserId)) {
                throw new RuntimeException("不能分享给自己");
            }
        }

        // 生成唯一分享码
        String shareCode = generateUniqueCode();

        // 创建分享记录
        DeviceShare share = new DeviceShare();
        share.setShareCode(shareCode);
        share.setDeviceId(deviceId);
        share.setOwnerUserId(ownerUserId);
        share.setPermission(permission);
        share.setStatus("pending");
        // 如果指定了目标用户，则在生成时就锁定 shared_user_id
        if (targetUser != null) {
            share.setSharedUserId(targetUser.getId());
        }
        share.setCreatedAt(new Date());

        // 设置过期时间
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.HOUR, SHARE_CODE_EXPIRE_HOURS);
        share.setExpireAt(calendar.getTime());

        deviceShareRepository.insert(share);

        log.info("生成分享码 - ownerUserId: {}, deviceId: {}, shareCode: {}, permission: {}, targetUserId: {}",
                ownerUserId, deviceId, shareCode, permission, targetUser != null ? targetUser.getId() : null);

        Map<String, Object> result = new HashMap<>();
        result.put("share_code", shareCode);
        result.put("permission", permission);
        result.put("expire_at", share.getExpireAt());
        // 二维码内容：可以是分享码本身，前端生成二维码
        result.put("qrcode_content", "PURA365_SHARE:" + shareCode);
        if (targetUser != null) {
            result.put("target_user_id", targetUser.getId());
            result.put("target_nickname", targetUser.getNickname());
            result.put("target_avatar", targetUser.getAvatar());
            result.put("target_username", targetUser.getUsername());
            result.put("target_phone", maskPhone(targetUser.getPhone()));
        }

        return result;
    }

    /**
     * 通过分享码绑定设备（扫码后调用）
     * @param userId 扫码用户ID
     * @param shareCode 分享码
     * @return 绑定结果
     */
    @Transactional
    public Map<String, Object> bindByShareCode(Long userId, String shareCode) {
        // 查找分享记录
        QueryWrapper<DeviceShare> qw = new QueryWrapper<>();
        qw.lambda().eq(DeviceShare::getShareCode, shareCode);
        DeviceShare share = deviceShareRepository.selectOne(qw);

        if (share == null) {
            throw new RuntimeException("分享码不存在");
        }

        // 检查状态
        if (!"pending".equals(share.getStatus())) {
            throw new RuntimeException("分享码已使用或已失效");
        }

        // 检查是否过期
        if (share.getExpireAt() != null && share.getExpireAt().before(new Date())) {
            share.setStatus("expired");
            deviceShareRepository.updateById(share);
            throw new RuntimeException("分享码已过期");
        }

        // 如果分享时已经指定了目标用户，则只允许该用户使用
        if (share.getSharedUserId() != null && !share.getSharedUserId().equals(userId)) {
            throw new RuntimeException("该分享仅限指定用户使用");
        }

        // 检查是否分享给自己
        if (share.getOwnerUserId().equals(userId)) {
            throw new RuntimeException("不能分享给自己");
        }

        // 检查用户是否已绑定该设备
        QueryWrapper<UserDevice> udQw = new QueryWrapper<>();
        udQw.lambda().eq(UserDevice::getUserId, userId)
                .eq(UserDevice::getDeviceId, share.getDeviceId());
        if (userDeviceRepository.selectCount(udQw) > 0) {
            throw new RuntimeException("您已绑定该设备");
        }

        // 创建用户设备关联
        UserDevice userDevice = new UserDevice();
        userDevice.setUserId(userId);
        userDevice.setDeviceId(share.getDeviceId());
        userDevice.setRole("viewer"); // 被分享者角色为viewer
        userDevice.setPermission(share.getPermission());
        userDevice.setCreatedAt(new Date());

        userDeviceRepository.insert(userDevice);

        // 更新分享记录状态
        share.setStatus("used");
        // 如果之前未指定 shared_user_id，则在此时写入扫码用户ID
        if (share.getSharedUserId() == null) {
            share.setSharedUserId(userId);
        }
        share.setUsedAt(new Date());
        deviceShareRepository.updateById(share);

        log.info("分享绑定成功 - userId: {}, deviceId: {}, permission: {}",
                userId, share.getDeviceId(), share.getPermission());

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("device_id", share.getDeviceId());
        result.put("permission", share.getPermission());

        return result;
    }

    /**
     * 获取设备的分享列表（谁被分享了这个设备）
     */
    public List<Map<String, Object>> getShareList(Long ownerUserId, String deviceId) {
        // 查询所有被分享的用户
        QueryWrapper<UserDevice> qw = new QueryWrapper<>();
        qw.lambda().eq(UserDevice::getDeviceId, deviceId)
                .eq(UserDevice::getRole, "viewer");
        List<UserDevice> viewers = userDeviceRepository.selectList(qw);

        List<Map<String, Object>> result = new ArrayList<>();
        for (UserDevice ud : viewers) {
            Map<String, Object> item = new HashMap<>();
            item.put("user_id", ud.getUserId());
            item.put("permission", ud.getPermission());
            item.put("created_at", ud.getCreatedAt());

            // 获取用户信息
            User user = userRepository.selectById(ud.getUserId());
            if (user != null) {
                item.put("nickname", user.getNickname());
                item.put("phone", maskPhone(user.getPhone()));
                item.put("avatar", user.getAvatar());
            }

            result.add(item);
        }

        return result;
    }

    /**
     * 取消分享（删除某个用户的设备访问权限）
     */
    @Transactional
    public boolean revokeShare(Long ownerUserId, String deviceId, Long targetUserId) {
        // 验证操作者是设备拥有者
        if (!isDeviceOwner(ownerUserId, deviceId)) {
            throw new RuntimeException("无权操作");
        }

        // 删除用户设备关联
        QueryWrapper<UserDevice> qw = new QueryWrapper<>();
        qw.lambda().eq(UserDevice::getUserId, targetUserId)
                .eq(UserDevice::getDeviceId, deviceId)
                .eq(UserDevice::getRole, "viewer");
        int deleted = userDeviceRepository.delete(qw);

        if (deleted > 0) {
            log.info("取消分享成功 - ownerUserId: {}, deviceId: {}, targetUserId: {}",
                    ownerUserId, deviceId, targetUserId);
            return true;
        }

        return false;
    }

    /**
     * 更新分享权限
     */
    public boolean updatePermission(Long ownerUserId, String deviceId, Long targetUserId, String permission) {
        // 验证操作者是设备拥有者
        if (!isDeviceOwner(ownerUserId, deviceId)) {
            throw new RuntimeException("无权操作");
        }

        // 验证权限参数
        if (!"view_only".equals(permission) && !"full_control".equals(permission)) {
            throw new RuntimeException("无效的权限参数");
        }

        // 更新权限
        QueryWrapper<UserDevice> qw = new QueryWrapper<>();
        qw.lambda().eq(UserDevice::getUserId, targetUserId)
                .eq(UserDevice::getDeviceId, deviceId)
                .eq(UserDevice::getRole, "viewer");
        UserDevice ud = userDeviceRepository.selectOne(qw);

        if (ud == null) {
            throw new RuntimeException("分享记录不存在");
        }

        ud.setPermission(permission);
        userDeviceRepository.updateById(ud);

        log.info("更新分享权限 - deviceId: {}, targetUserId: {}, permission: {}",
                deviceId, targetUserId, permission);

        return true;
    }

    /**
     * 检查用户对设备的权限
     * @return permission: view_only, full_control, owner, null(无权限)
     */
    public String checkPermission(Long userId, String deviceId) {
        QueryWrapper<UserDevice> qw = new QueryWrapper<>();
        qw.lambda().eq(UserDevice::getUserId, userId)
                .eq(UserDevice::getDeviceId, deviceId);
        UserDevice ud = userDeviceRepository.selectOne(qw);

        if (ud == null) {
            return null;
        }

        if ("owner".equals(ud.getRole())) {
            return "owner";
        }

        return ud.getPermission();
    }

    /**
     * 生成唯一分享码
     */
    private String generateUniqueCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    /**
     * 手机号脱敏
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
