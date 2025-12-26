package com.pura365.camera.controller.internal;

import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.model.share.*;
import com.pura365.camera.service.DeviceShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 设备分享接口
 *
 * - POST /share/generate     生成分享码（二维码内容）
 * - POST /share/bind         通过分享码绑定设备（扫码后调用）
 * - GET  /share/list         获取设备的分享列表
 * - POST /share/revoke       取消分享
 * - POST /share/permission   更新分享权限
 */
@Tag(name = "设备分享", description = "设备分享相关接口")
@RestController
@RequestMapping("/api/internal/share")
public class DeviceShareController {

    private static final Logger log = LoggerFactory.getLogger(DeviceShareController.class);

    @Autowired
    private DeviceShareService deviceShareService;

    /**
     * 生成分享码
     * POST /share/generate
     */
    @Operation(summary = "生成分享码", description = "生成设备分享码，用于生成二维码供他人扫码绑定")
    @PostMapping("/generate")
    public ApiResponse<ShareGenerateResponse> generateShareCode(
            @RequestAttribute("currentUserId") Long currentUserId,
            @RequestBody ShareGenerateRequest request) {

        String deviceId = request.getDeviceId();
        String permission = request.getPermission();
        String targetAccount = request.getTargetAccount();

        if (deviceId == null || deviceId.isEmpty()) {
            return ApiResponse.error(400, "device_id不能为空");
        }

        // 验证是设备拥有者
        if (!deviceShareService.isDeviceOwner(currentUserId, deviceId)) {
            return ApiResponse.error(403, "只有设备拥有者才能分享设备");
        }

        try {
            Map<String, Object> result = deviceShareService.generateShareCode(currentUserId, deviceId, permission, targetAccount);
            ShareGenerateResponse response = new ShareGenerateResponse();
            response.setShareCode((String) result.get("share_code"));
            response.setQrContent((String) result.get("qr_content"));
            response.setExpireAt((String) result.get("expire_at"));
            response.setDeviceId((String) result.get("device_id"));
            response.setPermission((String) result.get("permission"));
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("生成分享码失败", e);
            return ApiResponse.error(500, e.getMessage());
        }
    }

    /**
     * 通过分享码绑定设备（扫码后调用）
     * POST /share/bind
     */
    @Operation(summary = "扫码绑定设备", description = "通过分享码绑定设备，获得设备访问权限")
    @PostMapping("/bind")
    public ApiResponse<ShareBindResponse> bindByShareCode(
            @RequestAttribute("currentUserId") Long currentUserId,
            @RequestBody ShareBindRequest request) {

        String shareCode = request.getShareCode();

        if (shareCode == null || shareCode.isEmpty()) {
            return ApiResponse.error(400, "share_code不能为空");
        }

        // 如果是PURA365_SHARE:前缀格式，提取分享码
        if (shareCode.startsWith("PURA365_SHARE:")) {
            shareCode = shareCode.substring("PURA365_SHARE:".length());
        }

        try {
            Map<String, Object> result = deviceShareService.bindByShareCode(currentUserId, shareCode);
            ShareBindResponse response = new ShareBindResponse();
            response.setSuccess((Boolean) result.get("success"));
            response.setDeviceId((String) result.get("device_id"));
            response.setDeviceName((String) result.get("device_name"));
            response.setPermission((String) result.get("permission"));
            response.setOwnerId((Long) result.get("owner_id"));
            return ApiResponse.success(response);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("绑定设备失败", e);
            return ApiResponse.error(500, "绑定失败");
        }
    }

    /**
     * 获取设备的分享列表
     * GET /share/list?device_id=xxx
     */
    @Operation(summary = "获取分享列表", description = "获取设备的分享用户列表，查看哪些用户有访问权限")
    @GetMapping("/list")
    public ApiResponse<List<ShareUserVO>> getShareList(
            @RequestAttribute("currentUserId") Long currentUserId,
            @Parameter(description = "设备ID", required = true, example = "DEVICE123456")
            @RequestParam("device_id") String deviceId) {

        if (deviceId == null || deviceId.isEmpty()) {
            return ApiResponse.error(400, "device_id不能为空");
        }

        // 验证是设备拥有者
        if (!deviceShareService.isDeviceOwner(currentUserId, deviceId)) {
            return ApiResponse.error(403, "只有设备拥有者才能查看分享列表");
        }

        try {
            List<Map<String, Object>> list = deviceShareService.getShareList(currentUserId, deviceId);
            List<ShareUserVO> result = new ArrayList<>();
            for (Map<String, Object> item : list) {
                ShareUserVO vo = new ShareUserVO();
                vo.setUserId((Long) item.get("user_id"));
                vo.setUsername((String) item.get("username"));
                vo.setNickname((String) item.get("nickname"));
                vo.setAvatar((String) item.get("avatar"));
                vo.setPermission((String) item.get("permission"));
                vo.setSharedAt((String) item.get("shared_at"));
                result.add(vo);
            }
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取分享列表失败", e);
            return ApiResponse.error(500, "获取失败");
        }
    }

    /**
     * 取消分享（移除某用户的访问权限）
     * POST /share/revoke
     */
    @Operation(summary = "取消分享", description = "移除某用户对设备的访问权限，只有设备拥有者可操作")
    @PostMapping("/revoke")
    public ApiResponse<Void> revokeShare(
            @RequestAttribute("currentUserId") Long currentUserId,
            @RequestBody ShareRevokeRequest request) {

        String deviceId = request.getDeviceId();
        Long targetUserId = request.getUserId();

        if (deviceId == null || deviceId.isEmpty()) {
            return ApiResponse.error(400, "device_id不能为空");
        }
        if (targetUserId == null) {
            return ApiResponse.error(400, "user_id不能为空");
        }

        try {
            boolean success = deviceShareService.revokeShare(currentUserId, deviceId, targetUserId);
            if (success) {
                return ApiResponse.success(null);
            } else {
                return ApiResponse.error(404, "分享记录不存在");
            }
        } catch (RuntimeException e) {
            return ApiResponse.error(403, e.getMessage());
        } catch (Exception e) {
            log.error("取消分享失败", e);
            return ApiResponse.error(500, "操作失败");
        }
    }

    /**
     * 更新分享权限
     * POST /share/permission
     */
    @Operation(summary = "更新分享权限", description = "修改某用户对设备的权限，只有设备拥有者可操作")
    @PostMapping("/permission")
    public ApiResponse<Void> updatePermission(
            @RequestAttribute("currentUserId") Long currentUserId,
            @RequestBody SharePermissionUpdateRequest request) {

        String deviceId = request.getDeviceId();
        Long targetUserId = request.getUserId();
        String permission = request.getPermission();

        if (deviceId == null || deviceId.isEmpty()) {
            return ApiResponse.error(400, "device_id不能为空");
        }
        if (targetUserId == null) {
            return ApiResponse.error(400, "user_id不能为空");
        }
        if (permission == null || permission.isEmpty()) {
            return ApiResponse.error(400, "permission不能为空");
        }

        try {
            boolean success = deviceShareService.updatePermission(currentUserId, deviceId, targetUserId, permission);
            if (success) {
                return ApiResponse.success(null);
            } else {
                return ApiResponse.error(500, "更新失败");
            }
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("更新权限失败", e);
            return ApiResponse.error(500, "操作失败");
        }
    }

    /**
     * 检查当前用户对设备的权限
     * GET /share/check-permission?device_id=xxx
     */
    @Operation(summary = "检查设备权限", description = "检查当前用户对设备的权限，包括查看、控制等权限")
    @GetMapping("/check-permission")
    public ApiResponse<SharePermissionVO> checkPermission(
            @RequestAttribute("currentUserId") Long currentUserId,
            @Parameter(description = "设备ID", required = true, example = "DEVICE123456")
            @RequestParam("device_id") String deviceId) {

        if (deviceId == null || deviceId.isEmpty()) {
            return ApiResponse.error(400, "device_id不能为空");
        }

        String permission = deviceShareService.checkPermission(currentUserId, deviceId);

        SharePermissionVO response = new SharePermissionVO();
        response.setDeviceId(deviceId);
        response.setPermission(permission);
        response.setCanView(permission != null);
        response.setCanControl("owner".equals(permission) || "full_control".equals(permission));
        response.setIsOwner("owner".equals(permission));

        return ApiResponse.success(response);
    }
}
