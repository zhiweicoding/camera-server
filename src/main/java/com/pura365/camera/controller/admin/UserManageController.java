package com.pura365.camera.controller.admin;

import com.pura365.camera.domain.User;
import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.service.UserManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户管理接口（管理员使用）
 * 
 * 包含：
 * - 分页查询用户列表
 * - 获取用户详情
 * - 创建用户
 * - 更新用户
 * - 删除用户
 * - 重置密码
 * - 更新角色
 */
@Tag(name = "用户管理", description = "用户信息管理相关接口（管理员）")
@RestController
@RequestMapping("/api/admin/users")
public class UserManageController {

    @Autowired
    private UserManageService userManageService;

    /**
     * 分页查询用户列表
     */
    @Operation(summary = "分页查询用户", description = "支持关键词搜索和角色筛选")
    @GetMapping
    public ApiResponse<Map<String, Object>> listUsers(
            @Parameter(description = "搜索关键词（用户名/手机号/邮箱/昵称）") @RequestParam(required = false) String keyword,
            @Parameter(description = "角色筛选：1-流通用户 2-经销商 3-管理员") @RequestParam(required = false) Integer role,
            @Parameter(description = "页码（从1开始）") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int pageSize) {
        Map<String, Object> data = userManageService.listUsers(keyword, role, page, pageSize);
        return ApiResponse.success(data);
    }

    /**
     * 获取所有用户（不分页）
     */
    @Operation(summary = "获取所有用户", description = "获取所有用户列表（不分页）")
    @GetMapping("/all")
    public ApiResponse<List<User>> listAllUsers() {
        return ApiResponse.success(userManageService.listAllUsers());
    }

    /**
     * 获取单个用户详情
     */
    @Operation(summary = "获取用户详情", description = "根据ID获取用户详细信息")
    @GetMapping("/{id}")
    public ApiResponse<User> getUser(@PathVariable Long id) {
        User user = userManageService.getUserById(id);
        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }
        // 不返回密码哈希
        user.setPasswordHash(null);
        return ApiResponse.success(user);
    }

    /**
     * 创建用户
     * 请求体示例:
     * {
     *   "username": "testuser",
     *   "phone": "13800138000",
     *   "email": "test@example.com",
     *   "nickname": "测试用户",
     *   "role": 1,
     *   "password": "123456"
     * }
     */
    @Operation(summary = "创建用户", description = "创建新用户")
    @PostMapping
    public ApiResponse<User> createUser(@RequestBody Map<String, Object> body) {
        try {
            User user = new User();
            user.setUsername((String) body.get("username"));
            user.setPhone((String) body.get("phone"));
            user.setEmail((String) body.get("email"));
            user.setNickname((String) body.get("nickname"));
            user.setAvatar((String) body.get("avatar"));
            if (body.get("role") != null) {
                user.setRole(Integer.valueOf(body.get("role").toString()));
            }
            String password = (String) body.get("password");

            User created = userManageService.createUser(user, password);
            // 不返回密码哈希
            created.setPasswordHash(null);
            return ApiResponse.success(created);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 更新用户信息
     */
    @Operation(summary = "更新用户", description = "更新用户信息")
    @PutMapping("/{id}")
    public ApiResponse<User> updateUser(@PathVariable Long id, @RequestBody User user) {
        try {
            user.setId(id);
            User updated = userManageService.updateUser(user);
            // 不返回密码哈希
            updated.setPasswordHash(null);
            return ApiResponse.success(updated);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 删除用户
     */
    @Operation(summary = "删除用户", description = "删除指定用户")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        try {
            userManageService.deleteUser(id);
            return ApiResponse.success(null);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 重置用户密码
     * 请求体: { "password": "新密码" }
     */
    @Operation(summary = "重置密码", description = "重置用户密码")
    @PutMapping("/{id}/password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            String password = body.get("password");
            userManageService.resetPassword(id, password);
            return ApiResponse.success(null);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 更新用户角色
     * 请求体: { "role": 1 }
     */
    @Operation(summary = "更新角色", description = "更新用户角色：1-流通用户 2-经销商 3-管理员")
    @PutMapping("/{id}/role")
    public ApiResponse<Void> updateUserRole(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        try {
            Integer role = body.get("role");
            userManageService.updateUserRole(id, role);
            return ApiResponse.success(null);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
