package com.pura365.camera.model.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 登录响应
 */
@Schema(description = "登录响应")
public class LoginResponse {

    /**
     * 访问令牌
     */
    @Schema(description = "访问令牌", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    @JsonProperty("token")
    private String token;

    /**
     * 刷新令牌
     */
    @Schema(description = "刷新令牌", example = "a1b2c3d4e5f6...")
    @JsonProperty("refresh_token")
    private String refreshToken;

    /**
     * 令牌过期时间(秒)
     */
    @Schema(description = "令牌过期时间(秒)", example = "7200")
    @JsonProperty("expires_in")
    private Long expiresIn;

    /**
     * 用户信息
     */
    @Schema(description = "用户信息")
    @JsonProperty("user")
    private UserInfo user;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public Long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public UserInfo getUser() {
        return user;
    }

    public void setUser(UserInfo user) {
        this.user = user;
    }

    /**
     * 用户信息
     */
    @Schema(description = "用户信息")
    public static class UserInfo {

        @Schema(description = "用户ID")
        @JsonProperty("id")
        private String id;

        @Schema(description = "手机号")
        @JsonProperty("phone")
        private String phone;

        @Schema(description = "昵称")
        @JsonProperty("nickname")
        private String nickname;

        @Schema(description = "头像")
        @JsonProperty("avatar")
        private String avatar;

        @Schema(description = "角色")
        @JsonProperty("role")
        private String role;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getNickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }

        public String getAvatar() {
            return avatar;
        }

        public void setAvatar(String avatar) {
            this.avatar = avatar;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }
}
