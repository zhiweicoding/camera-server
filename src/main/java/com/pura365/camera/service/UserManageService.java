package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pura365.camera.domain.User;
import com.pura365.camera.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户管理服务（管理员使用）
 */
@Service
public class UserManageService {

    @Autowired
    private UserRepository userRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private static final int ROLE_ADMIN = 3;

    /**
     * 分页查询用户列表（排除管理员）
     *
     * @param keyword     搜索关键词（可选，匹配用户名、手机号、邮箱、昵称）
     * @param role        角色筛选（可选）
     * @param isInstaller 是否装机商筛选（可选）
     * @param isDealer    是否经销商筛选（可选）
     * @param page        页码（从1开始）
     * @param pageSize    每页大小
     * @return 分页结果
     */
    public Map<String, Object> listUsers(String keyword, Integer role, Integer isInstaller, Integer isDealer, int page, int pageSize) {
        Page<User> pageObj = new Page<>(page, pageSize);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

        // 排除管理员
        wrapper.ne(User::getRole, ROLE_ADMIN);

        // 关键词搜索
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getPhone, keyword)
                    .or().like(User::getEmail, keyword)
                    .or().like(User::getNickname, keyword));
        }

        // 角色筛选（不允许筛选管理员）
        if (role != null && role != ROLE_ADMIN) {
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

        Map<String, Object> data = new HashMap<>();
        data.put("total", result.getTotal());
        data.put("page", result.getCurrent());
        data.put("pageSize", result.getSize());
        data.put("totalPages", result.getPages());
        data.put("list", result.getRecords());
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
        return user;
    }

    /**
     * 更新用户信息（不允许修改管理员）
     */
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
        }
        if (user.getIsDealer() != null) {
            existing.setIsDealer(user.getIsDealer());
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
        if (user.getRole() != null && user.getRole() == ROLE_ADMIN) {
            throw new RuntimeException("不允许重置管理员密码");
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
