package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pura365.camera.domain.Dealer;
import com.pura365.camera.domain.Installer;
import com.pura365.camera.domain.User;
import com.pura365.camera.enums.EnableStatus;
import com.pura365.camera.repository.DealerRepository;
import com.pura365.camera.repository.InstallerRepository;
import com.pura365.camera.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户管理服务（管理员使用）
 */
@Service
public class UserManageService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InstallerRepository installerRepository;

    @Autowired
    private DealerRepository dealerRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private static final int ROLE_ADMIN = 3;

    // 装机商代码字符集: 0-9, A-Z, a-z (共62个字符)
    private static final String INSTALLER_CODE_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    // 经销商代码字符集: 0-9, A-Z, a-z (共62个字符，两位组合)
    private static final String DEALER_CODE_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    /**
     * 分页查询用户列表（排除管理员）
     *
     * @param keyword     搜索关键词（可选，匹配用户名、手机号、邮箱、昵称）
     * @param role        角色筛选（可选）
     * @param isInstaller 是否装机商筛选（可选）
     * @param isDealer    是否经销商筛选（可选）
     * @param userType    用户类型：consumer-普通使用者(无身份) staff-有身份用户
     * @param page        页码（从1开始）
     * @param pageSize    每页大小
     * @return 分页结果
     */
    public Map<String, Object> listUsers(String keyword, Integer role, Integer isInstaller, Integer isDealer, String userType, int page, int pageSize) {
        Page<User> pageObj = new Page<>(page, pageSize);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

        // 用户类型筛选
        if ("consumer".equals(userType)) {
            // 使用者管理：只显示普通用户(role=1)
            wrapper.eq(User::getRole, 1);
        } else if ("staff".equals(userType)) {
            // 用户管理：管理员(role=3) + 操作员(role=2)，排除普通用户(role=1)
            wrapper.ne(User::getRole, 1);
        }

        // 关键词搜索
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getPhone, keyword)
                    .or().like(User::getEmail, keyword)
                    .or().like(User::getNickname, keyword));
        }

        // 角色筛选
        if (role != null) {
            wrapper.eq(User::getRole, role);
        }

        // 装机商身份筛选
        if (isInstaller != null) {
            wrapper.eq(User::getIsInstaller, isInstaller);
        }

        // 经销商身份筛选
        if (isDealer != null) {
            wrapper.eq(User::getIsDealer, isDealer);
        }

        // 按创建时间倒序
        wrapper.orderByDesc(User::getCreatedAt);

        Page<User> result = userRepository.selectPage(pageObj, wrapper);

        // 获取用户列表并补充装机商代码和经销商代码
        List<Map<String, Object>> userList = result.getRecords().stream().map(user -> {
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("id", user.getId());
            userMap.put("uid", user.getUid());
            userMap.put("username", user.getUsername());
            userMap.put("phone", user.getPhone());
            userMap.put("email", user.getEmail());
            userMap.put("nickname", user.getNickname());
            userMap.put("avatar", user.getAvatar());
            userMap.put("role", user.getRole());
            userMap.put("enabled", user.getEnabled());
            userMap.put("isInstaller", user.getIsInstaller());
            userMap.put("isDealer", user.getIsDealer());
            userMap.put("installerId", user.getInstallerId());
            userMap.put("dealerId", user.getDealerId());
            userMap.put("createdAt", user.getCreatedAt());
            userMap.put("updatedAt", user.getUpdatedAt());

            // 查询装机商代码
            if (user.getInstallerId() != null) {
                Installer installer = installerRepository.selectById(user.getInstallerId());
                if (installer != null) {
                    userMap.put("installerCode", installer.getInstallerCode());
                    userMap.put("installerName", installer.getInstallerName());
                }
            }

            // 查询经销商代码
            if (user.getDealerId() != null) {
                Dealer dealer = dealerRepository.selectById(user.getDealerId());
                if (dealer != null) {
                    userMap.put("dealerCode", dealer.getDealerCode());
                    userMap.put("dealerName", dealer.getName());
                }
            }

            return userMap;
        }).collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("total", result.getTotal());
        data.put("page", result.getCurrent());
        data.put("pageSize", result.getSize());
        data.put("totalPages", result.getPages());
        data.put("list", userList);
        return data;
    }

    /**
     * 获取所有用户（不分页，排除管理员）
     */
    public List<User> listAllUsers() {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.ne(User::getRole, ROLE_ADMIN);
        wrapper.orderByDesc(User::getCreatedAt);
        return userRepository.selectList(wrapper);
    }

    /**
     * 根据ID获取用户（排除管理员）
     */
    public User getUserById(Long id) {
        User user = userRepository.selectById(id);
        if (user != null && user.getRole() != null && user.getRole() == ROLE_ADMIN) {
            return null; // 管理员不可查
        }
        return user;
    }

    /**
     * 根据UID获取用户（排除管理员）
     */
    public User getUserByUid(String uid) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUid, uid);
        wrapper.ne(User::getRole, ROLE_ADMIN);
        return userRepository.selectOne(wrapper);
    }

    /**
     * 创建用户（不允许创建管理员）
     */
    @Transactional
    public User createUser(User user, String password) {
        // 不允许创建管理员
        if (user.getRole() != null && user.getRole() == ROLE_ADMIN) {
            throw new RuntimeException("不允许创建管理员账号");
        }

        // 检查账号是否已存在
        checkDuplicate(user, null);

        // 生成业务UID
        if (user.getUid() == null || user.getUid().isEmpty()) {
            user.setUid("user_" + System.currentTimeMillis());
        }

        // 设置密码
        if (password != null && !password.isEmpty()) {
            user.setPasswordHash(passwordEncoder.encode(password));
        }

        // 默认角色为流通用户
        if (user.getRole() == null) {
            user.setRole(1);
        }

        // 设置创建时间
        Date now = new Date();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        userRepository.insert(user);

        // 如果标记为装机商，自动创建installer记录
        if (user.getIsInstaller() != null && user.getIsInstaller() == 1) {
            createInstallerForUser(user);
        }

        // 如果标记为经销商，自动创建dealer记录
        if (user.getIsDealer() != null && user.getIsDealer() == 1) {
            createDealerForUser(user);
        }

        return user;
    }

    /**
     * 更新用户信息（不允许修改管理员）
     */
    @Transactional
    public User updateUser(User user) {
        User existing = userRepository.selectById(user.getId());
        if (existing == null) {
            throw new RuntimeException("用户不存在");
        }

        // 不允许修改管理员
        if (existing.getRole() != null && existing.getRole() == ROLE_ADMIN) {
            throw new RuntimeException("不允许修改管理员账号");
        }

        // 不允许将用户角色改为管理员
        if (user.getRole() != null && user.getRole() == ROLE_ADMIN) {
            throw new RuntimeException("不允许将用户设置为管理员");
        }

        // 检查重复
        checkDuplicate(user, user.getId());

        // 更新允许的字段
        if (user.getUsername() != null) {
            existing.setUsername(user.getUsername());
        }
        if (user.getPhone() != null) {
            existing.setPhone(user.getPhone());
        }
        if (user.getEmail() != null) {
            existing.setEmail(user.getEmail());
        }
        if (user.getNickname() != null) {
            existing.setNickname(user.getNickname());
        }
        if (user.getAvatar() != null) {
            existing.setAvatar(user.getAvatar());
        }
        if (user.getRole() != null) {
            existing.setRole(user.getRole());
        }
        // 双重身份字段
        if (user.getIsInstaller() != null) {
            existing.setIsInstaller(user.getIsInstaller());
            // 如果新增装机商身份，自动创建installer记录
            if (user.getIsInstaller() == 1 && existing.getInstallerId() == null) {
                createInstallerForUser(existing);
            }
        }
        if (user.getIsDealer() != null) {
            existing.setIsDealer(user.getIsDealer());
            // 如果新增经销商身份，自动创建dealer记录
            if (user.getIsDealer() == 1 && existing.getDealerId() == null) {
                createDealerForUser(existing);
            }
        }
        if (user.getInstallerId() != null) {
            existing.setInstallerId(user.getInstallerId());
        }
        if (user.getDealerId() != null) {
            existing.setDealerId(user.getDealerId());
        }

        existing.setUpdatedAt(new Date());
        userRepository.updateById(existing);
        return existing;
    }

    /**
     * 删除用户（不允许删除管理员）
     */
    public void deleteUser(Long id) {
        User user = userRepository.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (user.getRole() != null && user.getRole() == ROLE_ADMIN) {
            throw new RuntimeException("不允许删除管理员账号");
        }
        userRepository.deleteById(id);
    }

    /**
     * 重置用户密码（不允许重置管理员密码）
     */
    public void resetPassword(Long id, String newPassword) {
        User user = userRepository.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (newPassword == null || newPassword.isEmpty()) {
            throw new RuntimeException("新密码不能为空");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(new Date());
        userRepository.updateById(user);
    }

    /**
     * 更新用户角色（不允许修改管理员角色，不允许设置为管理员）
     */
    public void updateUserRole(Long id, Integer role) {
        User user = userRepository.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (user.getRole() != null && user.getRole() == ROLE_ADMIN) {
            throw new RuntimeException("不允许修改管理员角色");
        }
        if (role == null || (role != 1 && role != 2 && role != 4)) {
            throw new RuntimeException("角色值无效，必须为 1-流通用户 2-经销商 4-装机商");
        }
        user.setRole(role);
        user.setUpdatedAt(new Date());
        userRepository.updateById(user);
    }

    /**
     * 更新用户身份标识（装机商/经销商双重身份）
     */
    public void updateUserIdentity(Long id, Integer isInstaller, Integer isDealer, Long installerId, Long dealerId) {
        User user = userRepository.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (user.getRole() != null && user.getRole() == ROLE_ADMIN) {
            throw new RuntimeException("不允许修改管理员身份");
        }
        if (isInstaller != null) {
            user.setIsInstaller(isInstaller);
            if (isInstaller == 1 && installerId != null) {
                user.setInstallerId(installerId);
            } else if (isInstaller == 0) {
                user.setInstallerId(null);
            }
        }
        if (isDealer != null) {
            user.setIsDealer(isDealer);
            if (isDealer == 1 && dealerId != null) {
                user.setDealerId(dealerId);
            } else if (isDealer == 0) {
                user.setDealerId(null);
            }
        }
        user.setUpdatedAt(new Date());
        userRepository.updateById(user);
    }

    private static final int MAX_RETRY = 3;

    /**
     * 为用户创建装机商(Installer)记录
     * 装机商代码: 1位字符(0-9/A-Z/a-z)
     * 支持并发重试
     */
    private void createInstallerForUser(User user) {
        for (int retry = 0; retry < MAX_RETRY; retry++) {
            try {
                String installerCode = generateNextInstallerCode();

                Installer installer = new Installer();
                installer.setInstallerCode(installerCode);
                installer.setInstallerName(user.getNickname() != null ? user.getNickname() : user.getUsername());
                installer.setContactPerson(user.getNickname() != null ? user.getNickname() : user.getUsername());
                installer.setContactPhone(user.getPhone());
                installer.setStatus(EnableStatus.ENABLED);
                installer.setCreatedAt(new Date());
                installer.setUpdatedAt(new Date());

                installerRepository.insert(installer);

                // 更新用户的installer_id
                user.setInstallerId(installer.getId());
                userRepository.updateById(user);
                return; // 成功则返回
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 并发冲突，重试
                if (retry == MAX_RETRY - 1) {
                    throw new RuntimeException("创建装机商失败，请重试", e);
                }
            }
        }
    }

    /**
     * 为用户创建经销商(Dealer)记录
     * 经销商代码: 2位字符(0-9/A-Z/a-z组合)
     * 支持并发重试
     */
    private void createDealerForUser(User user) {
        for (int retry = 0; retry < MAX_RETRY; retry++) {
            try {
                String dealerCode = generateNextDealerCode();

                Dealer dealer = new Dealer();
                dealer.setDealerCode(dealerCode);
                // 如果用户同时是装机商，关联到自己的installer
                if (user.getInstallerId() != null) {
                    dealer.setInstallerId(user.getInstallerId());
                    Installer installer = installerRepository.selectById(user.getInstallerId());
                    if (installer != null) {
                        dealer.setInstallerCode(installer.getInstallerCode());
                    }
                }
                dealer.setName(user.getNickname() != null ? user.getNickname() : user.getUsername());
                dealer.setPhone(user.getPhone());
                dealer.setLevel(1); // 默认一级经销商
                dealer.setStatus(EnableStatus.ENABLED);
                dealer.setCreatedAt(new Date());
                dealer.setUpdatedAt(new Date());

                dealerRepository.insert(dealer);

                // 更新用户的dealer_id
                user.setDealerId(dealer.getId());
                userRepository.updateById(user);
                return; // 成功则返回
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 并发冲突，重试
                if (retry == MAX_RETRY - 1) {
                    throw new RuntimeException("创建经销商失败，请重试", e);
                }
            }
        }
    }

    /**
     * 生成下一个可用的装机商代码(1位: 0-9, A-Z, a-z)
     * 遍历所有可用字符，返回第一个未被使用的代码
     */
    private String generateNextInstallerCode() {
        // 获取所有已使用的代码
        LambdaQueryWrapper<Installer> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Installer::getInstallerCode);
        List<Installer> installers = installerRepository.selectList(wrapper);
        Set<String> usedCodes = installers.stream()
                .map(Installer::getInstallerCode)
                .filter(code -> code != null && !code.isEmpty())
                .collect(Collectors.toSet());

        // 遍历所有可用字符，找到第一个未使用的
        for (int i = 0; i < INSTALLER_CODE_CHARS.length(); i++) {
            String code = String.valueOf(INSTALLER_CODE_CHARS.charAt(i));
            if (!usedCodes.contains(code)) {
                return code;
            }
        }
        // 所有单字符代码都已用完，抛出异常
        throw new RuntimeException("装机商代码已用完（最多支持62个装机商）");
    }

    /**
     * 生成下一个可用的经销商代码(2位: 00-zz)
     * 遍历所有可用组合，返回第一个未被使用的代码
     */
    private String generateNextDealerCode() {
        // 获取所有已使用的代码
        LambdaQueryWrapper<Dealer> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Dealer::getDealerCode);
        List<Dealer> dealers = dealerRepository.selectList(wrapper);
        Set<String> usedCodes = dealers.stream()
                .map(Dealer::getDealerCode)
                .filter(code -> code != null && !code.isEmpty())
                .collect(Collectors.toSet());

        // 遍历所有2位组合，找到第一个未使用的
        for (int i = 0; i < DEALER_CODE_CHARS.length(); i++) {
            for (int j = 0; j < DEALER_CODE_CHARS.length(); j++) {
                String code = String.valueOf(DEALER_CODE_CHARS.charAt(i)) + DEALER_CODE_CHARS.charAt(j);
                if (!usedCodes.contains(code)) {
                    return code;
                }
            }
        }
        // 所有2位代码都已用完，抛出异常
        throw new RuntimeException("经销商代码已用完（最多支持3844个经销商）");
    }

    /**
     * 检查账号是否重复
     */
    private void checkDuplicate(User user, Long excludeId) {
        // 检查用户名
        if (user.getUsername() != null && !user.getUsername().isEmpty()) {
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(User::getUsername, user.getUsername());
            if (excludeId != null) {
                wrapper.ne(User::getId, excludeId);
            }
            if (userRepository.selectCount(wrapper) > 0) {
                throw new RuntimeException("用户名已存在");
            }
        }

        // 检查手机号
        if (user.getPhone() != null && !user.getPhone().isEmpty()) {
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(User::getPhone, user.getPhone());
            if (excludeId != null) {
                wrapper.ne(User::getId, excludeId);
            }
            if (userRepository.selectCount(wrapper) > 0) {
                throw new RuntimeException("手机号已存在");
            }
        }

        // 检查邮箱
        if (user.getEmail() != null && !user.getEmail().isEmpty()) {
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(User::getEmail, user.getEmail());
            if (excludeId != null) {
                wrapper.ne(User::getId, excludeId);
            }
            if (userRepository.selectCount(wrapper) > 0) {
                throw new RuntimeException("邮箱已存在");
            }
        }
    }
}
