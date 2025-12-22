package com.pura365.camera.controller.app;

import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.model.auth.WechatLoginRequest;
import com.pura365.camera.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户认证接口
 */
@Tag(name = "用户认证", description = "用户注册、登录、登出等认证相关接口")
@RestController
@RequestMapping("/api/app/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    /**
     * 密码注册
     */
    @Operation(summary = "密码注册", description = "使用用户名和密码注册新账号")
    @PostMapping("/register")
    public ApiResponse<?> register(@RequestBody Map<String, String> body) {
        try {
            String username = body.get("username");
            String phone = body.get("phone");
            String email = body.get("email");
            String password = body.get("password");
            Map<String, Object> data = authService.registerByPassword(username, phone, email, password);
            return ApiResponse.success(data);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "服务器错误");
        }
    }

    /**
     * 密码登录
     */
    @Operation(summary = "密码登录", description = "使用账号和密码登录")
    @PostMapping("/login/password")
    public ApiResponse<?> loginByPassword(@RequestBody Map<String, String> body) {
        try {
            String account = body.get("account");
            String password = body.get("password");
            if (account == null || password == null) {
                return ApiResponse.error(400, "account 和 password 不能为空");
            }
            Map<String, Object> data = authService.loginByPassword(account, password);
            return ApiResponse.success(data);
        } catch (RuntimeException e) {
            return ApiResponse.error(401, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "服务器错误");
        }
    }

    /**
     * 刷新访问令牌
     */
    @Operation(summary = "刷新令牌", description = "使用refresh_token刷新访问令牌")
    @PostMapping("/token/refresh")
    public ApiResponse<?> refreshToken(@RequestBody Map<String, String> body) {
        try {
            String refreshToken = body.get("refresh_token");
            if (refreshToken == null) {
                return ApiResponse.error(400, "refresh_token 不能为空");
            }
            Map<String, Object> data = authService.refreshToken(refreshToken);
            return ApiResponse.success(data);
        } catch (RuntimeException e) {
            return ApiResponse.error(401, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "服务器错误");
        }
    }

    /**
     * 修改密码
     */
    @Operation(summary = "修改密码", description = "用户修改密码，需验证旧密码")
    @PostMapping("/password/change")
    public ApiResponse<?> changePassword(
            @RequestHeader(value = "X-User-Id", required = true) String userId,
            @RequestBody Map<String, String> body) {
        try {
            String oldPassword = body.get("old_password");
            String newPassword = body.get("new_password");
            if (userId == null || userId.isEmpty()) {
                return ApiResponse.error(401, "未登录");
            }
            Long uid = Long.parseLong(userId);
            authService.changePassword(uid, oldPassword, newPassword);
            return ApiResponse.success(null);
        } catch (NumberFormatException e) {
            return ApiResponse.error(400, "无效的用户ID");
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "服务器错误");
        }
    }

    /**
     * 登出
     */
    @Operation(summary = "登出", description = "用户登出，失效token")
    @PostMapping("/logout")
    public ApiResponse<?> logout(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            String refreshToken = body != null ? body.get("refresh_token") : null;
            Long uid = null;
            if (userId != null && !userId.isEmpty()) {
                try {
                    uid = Long.parseLong(userId);
                } catch (NumberFormatException ignored) {
                    // 解析失败就按 null 处理，只根据 refreshToken 删除
                }
            }
            authService.logout(uid, refreshToken);
            return ApiResponse.success(null);
        } catch (Exception e) {
            return ApiResponse.error(500, "服务器错误");
        }
    }

    /**
     * 微信登录
     */
    @Operation(summary = "微信登录", description = "使用微信授权码登录，客户端通过微信SDK获取code后调用此接口")
    @PostMapping("/login/wechat")
    public ApiResponse<?> loginByWechat(@RequestBody WechatLoginRequest request) {
        try {
            // 参数校验
            if (!StringUtils.hasText(request.getCode())) {
                return ApiResponse.error(400, "code 不能为空");
            }
            
            // 调用服务层处理登录
            Map<String, Object> data = authService.loginByWeChat(request.getCode());
            return ApiResponse.success(data);
        } catch (RuntimeException e) {
            return ApiResponse.error(401, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "服务器错误");
        }
    }

    /**
     * Apple登录
     */
    @Operation(summary = "Apple登录", description = "使用Apple ID登录")
    @PostMapping("/login/apple")
    public ApiResponse<?> loginByApple(@RequestBody Map<String, String> body) {
        try {
            String identityToken = body.get("identity_token");
            if (identityToken == null) {
                return ApiResponse.error(400, "identity_token 不能为空");
            }
            String firstName = body.get("first_name");
            String lastName = body.get("last_name");
            String email = body.get("email");

            Map<String, String> userInfo = new HashMap<>();
            if (firstName != null) {
                userInfo.put("firstName", firstName);
            }
            if (lastName != null) {
                userInfo.put("lastName", lastName);
            }
            if (email != null) {
                userInfo.put("email", email);
            }

            Map<String, Object> data = authService.loginByApple(identityToken, userInfo);
            return ApiResponse.success(data);
        } catch (RuntimeException e) {
            return ApiResponse.error(401, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "服务器错误");
        }
    }

    /**
     * Google登录
     */
    @Operation(summary = "Google登录", description = "使用Google ID Token登录")
    @PostMapping("/login/google")
    public ApiResponse<?> loginByGoogle(@RequestBody Map<String, String> body) {
        try {
            String idToken = body.get("id_token");
            if (idToken == null) {
                return ApiResponse.error(400, "id_token 不能为空");
            }
            String platform = body.getOrDefault("platform", "web");
            Map<String, Object> data = authService.loginByGoogle(idToken, platform);
            return ApiResponse.success(data);
        } catch (RuntimeException e) {
            return ApiResponse.error(401, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "服务器错误");
        }
    }

    /**
     * Google OAuth 回调接口
     * 用于Web端Google登录的回调处理
     */
    @Operation(summary = "Google OAuth回调", description = "Google授权登录回调接口，用于接收授权码并完成登录")
    @GetMapping("/callback/google")
    public ApiResponse<?> googleOAuthCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "state", required = false) String state) {
        try {
            if (error != null) {
                return ApiResponse.error(401, "Google授权被拒绝: " + error);
            }
            if (code == null || code.isEmpty()) {
                return ApiResponse.error(400, "授权码不能为空");
            }
            Map<String, Object> data = authService.loginByGoogleCode(code);
            return ApiResponse.success(data);
        } catch (RuntimeException e) {
            return ApiResponse.error(401, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "服务器错误");
        }
    }

    /**
     * 发送短信验证码
     */
    @Operation(summary = "发送短信验证码", description = "发送短信验证码（暂未实现）")
    @PostMapping("/sms/send")
    public ApiResponse<?> sendSms(@RequestBody Map<String, String> body) {
        return ApiResponse.error(501, "短信发送暂未实现");
    }

    /**
     * 短信登录
     */
    @Operation(summary = "短信登录", description = "使用短信验证码登录（暂未实现）")
    @PostMapping("/login/sms")
    public ApiResponse<?> loginBySms(@RequestBody Map<String, String> body) {
        return ApiResponse.error(501, "短信登录暂未实现");
    }
}