package com.pura365.camera.controller.app;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.pura365.camera.domain.User;
import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * App 用户相关接口
 * 
 * 包含：
 * - 获取用户信息
 * - 更新用户信息
 * - 上传头像
 */
@Tag(name = "用户信息", description = "用户信息查询和更新接口")
@RestController
@RequestMapping("/api/app/user")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserRepository userRepository;

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> getUserInfo(@RequestAttribute(value = "currentUserId", required = false) Long currentUserId) {
        log.info("获取用户信息 - userId={}", currentUserId);
        if (currentUserId == null) {
            log.warn("获取用户信息失败 - 未登录");
            return ApiResponse.error(401, "未登录");
        }
        User user = userRepository.selectById(currentUserId);
        if (user == null) {
            log.warn("获取用户信息失败 - 用户不存在, userId={}", currentUserId);
            return ApiResponse.error(404, "用户不存在");
        }
        Map<String, Object> data = buildUserInfo(user);
        return ApiResponse.success(data);
    }

    /**
     * 更新用户信息（昵称、头像）
     */
    @PutMapping("/update")
    public ApiResponse<Map<String, Object>> updateUser(@RequestAttribute(value = "currentUserId", required = false) Long currentUserId,
                                                       @RequestBody Map<String, String> body) {
        log.info("更新用户信息 - userId={}, body={}", currentUserId, body);
        if (currentUserId == null) {
            return ApiResponse.error(401, "未登录");
        }
        User user = userRepository.selectById(currentUserId);
        if (user == null) {
            log.warn("更新用户信息失败 - 用户不存在, userId={}", currentUserId);
            return ApiResponse.error(404, "用户不存在");
        }
        String nickname = body.get("nickname");
        String avatar = body.get("avatar");
        if (nickname != null) {
            user.setNickname(nickname);
        }
        if (avatar != null) {
            user.setAvatar(avatar);
        }
        userRepository.updateById(user);
        log.info("更新用户信息成功 - userId={}", currentUserId);
        Map<String, Object> data = buildUserInfo(user);
        return ApiResponse.success(data);
    }

    /**
     * 上传头像文件
     */
    @PostMapping("/avatar")
    public ApiResponse<Map<String, Object>> uploadAvatar(@RequestAttribute(value = "currentUserId", required = false) Long currentUserId,
                                                         @RequestParam("file") MultipartFile file) {
        log.info("上传头像 - userId={}, fileName={}", currentUserId, file != null ? file.getOriginalFilename() : null);
        if (currentUserId == null) {
            return ApiResponse.error(401, "未登录");
        }
        if (file == null || file.isEmpty()) {
            log.warn("上传头像失败 - 文件为空, userId={}", currentUserId);
            return ApiResponse.error(400, "文件不能为空");
        }
        User user = userRepository.selectById(currentUserId);
        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }
        try {
            String avatarUrl = saveAvatarFile(file);
            user.setAvatar(avatarUrl);
            userRepository.updateById(user);
            log.info("上传头像成功 - userId={}, url={}", currentUserId, avatarUrl);
            Map<String, Object> data = new HashMap<>();
            data.put("url", avatarUrl);
            return ApiResponse.success(data);
        } catch (IOException e) {
            log.error("上传头像异常 - userId={}", currentUserId, e);
            return ApiResponse.error(500, "上传失败");
        }
    }

    private Map<String, Object> buildUserInfo(User user) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", user.getUid());
        data.put("phone", user.getPhone());
        data.put("nickname", user.getNickname());
        data.put("avatar", user.getAvatar());
        data.put("email", user.getEmail());
        data.put("role", user.getRole());
        if (user.getCreatedAt() != null) {
            data.put("created_at", formatIsoTime(user.getCreatedAt()));
        }
        return data;
    }

    private String formatIsoTime(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return sdf.format(date);
    }

    private String saveAvatarFile(MultipartFile file) throws IOException {
        String baseDir = System.getProperty("user.dir") + File.separator + "uploads" + File.separator + "avatars";
        File dir = new File(baseDir);
        if (!dir.exists()) {
            boolean ok = dir.mkdirs();
            if (!ok) {
                throw new IOException("无法创建上传目录");
            }
        }
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.'));
        }
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;
        File dest = new File(dir, filename);
        file.transferTo(dest);
        // 返回相对 URL，前端自行拼接域名
        return "/uploads/avatars/" + filename;
    }
}
