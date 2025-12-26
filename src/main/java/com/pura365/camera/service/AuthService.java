package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.domain.User;
import com.pura365.camera.domain.UserAuth;
import com.pura365.camera.domain.UserToken;
import com.pura365.camera.repository.UserAuthRepository;
import com.pura365.camera.repository.UserRepository;
import com.pura365.camera.repository.UserTokenRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 认证相关业务：账号密码登录、token 生成与刷新
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    // TODO: 如果你有正式配置，可以把 SECRET_KEY 放到配置文件
    private static final String SECRET_KEY = "app-user-jwt-secret-camera-server";
    private static final long ACCESS_TOKEN_EXPIRE_SECONDS = 259200; // 72 小时

    private static final String AUTH_TYPE_WECHAT = "wechat";
    private static final String AUTH_TYPE_APPLE = "apple";
    private static final String AUTH_TYPE_GOOGLE = "google";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserTokenRepository userTokenRepository;

    @Autowired
    private UserAuthRepository userAuthRepository;

    @Autowired
    private OAuthService oAuthService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 注册一个新用户（账号密码），并返回登录结果
     */
    public Map<String, Object> registerByPassword(String username, String phone, String email, String password) {
        if ((username == null || username.isEmpty())
                && (phone == null || phone.isEmpty())
                && (email == null || email.isEmpty())) {
            throw new RuntimeException("username、phone、email 至少填一个");
        }
        if (password == null || password.isEmpty()) {
            throw new RuntimeException("password 不能为空");
        }

        // 检查是否已存在相同账号
        QueryWrapper<User> qw = new QueryWrapper<>();
        // 直接使用 LambdaQueryWrapper 构造 username/phone/email 的 OR 条件
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User> lw = qw.lambda();
        boolean hasCond = false;
        if (username != null && !username.isEmpty()) {
            lw.eq(User::getUsername, username);
            hasCond = true;
        }
        if (phone != null && !phone.isEmpty()) {
            if (hasCond) {
                lw.or();
            }
            lw.eq(User::getPhone, phone);
            hasCond = true;
        }
        if (email != null && !email.isEmpty()) {
            if (hasCond) {
                lw.or();
            }
            lw.eq(User::getEmail, email);
        }
        User exist = userRepository.selectOne(lw);
        if (exist != null) {
            throw new RuntimeException("账号已存在");
        }

        User user = new User();
        // 简单生成业务 uid
        user.setUid("user_" + System.currentTimeMillis());
        user.setUsername(username);
        user.setPhone(phone);
        user.setEmail(email);
        // 默认角色：1-流通用户
        user.setRole(1);
        setUserPassword(user, password);
        if (username != null && !username.isEmpty()) {
            user.setNickname(username);
        } else if (phone != null && !phone.isEmpty()) {
            user.setNickname(phone);
        }
        userRepository.insert(user);

        return buildLoginResult(user);
    }

    /**
     * 账号密码登录
     */
    public Map<String, Object> loginByPassword(String account, String password) {
        // account 可以是 username / phone / email
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.lambda().and(q -> q.eq(User::getUsername, account)
                .or().eq(User::getPhone, account)
                .or().eq(User::getEmail, account));
        User user = userRepository.selectOne(qw);
        if (user == null || user.getPasswordHash() == null) {
            throw new RuntimeException("账号或密码错误");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new RuntimeException("账号或密码错误");
        }
        return buildLoginResult(user);
    }

    /**
     * 用 refresh_token 刷新 access token
     */
    public Map<String, Object> refreshToken(String refreshToken) {
        QueryWrapper<UserToken> qw = new QueryWrapper<>();
        qw.lambda().eq(UserToken::getRefreshToken, refreshToken);
        UserToken ut = userTokenRepository.selectOne(qw);
        if (ut == null) {
            throw new RuntimeException("无效的 refresh_token");
        }
        User user = userRepository.selectById(ut.getUserId());
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        // 重新生成 access_token（也可以同时旋转 refresh_token）
        return buildLoginResult(user);
    }

    /**
     * 修改密码
     * @param userId 用户ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     */
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        if (userId == null) {
            throw new RuntimeException("用户ID不能为空");
        }
        if (oldPassword == null || oldPassword.isEmpty()) {
            throw new RuntimeException("旧密码不能为空");
        }
        if (newPassword == null || newPassword.isEmpty()) {
            throw new RuntimeException("新密码不能为空");
        }
        if (newPassword.length() < 6) {
            throw new RuntimeException("新密码长度不能少于6位");
        }

        User user = userRepository.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (user.getPasswordHash() == null) {
            throw new RuntimeException("该账号未设置密码，无法修改");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new RuntimeException("旧密码错误");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(new Date());
        userRepository.updateById(user);
        log.info("用户修改密码成功: userId={}", userId);
    }

    /**
     * 退出登录：删除当前 refresh_token 记录
     */
    public void logout(Long userId, String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            // 如果没有带，直接按 userId 删除所有 token 记录
            if (userId != null) {
                QueryWrapper<UserToken> qw = new QueryWrapper<>();
                qw.lambda().eq(UserToken::getUserId, userId);
                userTokenRepository.delete(qw);
            }
            return;
        }
        QueryWrapper<UserToken> qw = new QueryWrapper<>();
        qw.lambda().eq(UserToken::getRefreshToken, refreshToken);
        userTokenRepository.delete(qw);
    }

    /**
     * 校验并解析 access token，返回 userId；无效则返回 null
     */
    public Long parseUserIdFromAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(SECRET_KEY)
                    .parseClaimsJws(token)
                    .getBody();
            Object uidObj = claims.get("uid");
            if (uidObj == null) return null;
            return Long.valueOf(uidObj.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> buildLoginResult(User user) {
        long now = System.currentTimeMillis();
        long expMillis = now + ACCESS_TOKEN_EXPIRE_SECONDS * 1000;
        Date issuedAt = new Date(now);
        Date expiresAt = new Date(expMillis);

        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", user.getId()); // 这里放数据库自增ID，前端返回显示用 user.uid

        String accessToken = Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(issuedAt)
                .setExpiration(expiresAt)
                .signWith(SignatureAlgorithm.HS256, SECRET_KEY)
                .compact();

        String refreshToken = UUID.randomUUID().toString().replace("-", "");

        // 持久化 token 信息
        UserToken ut = new UserToken();
        ut.setUserId(user.getId());
        ut.setAccessToken(accessToken);
        ut.setRefreshToken(refreshToken);
        ut.setExpiresAt(expiresAt);
        userTokenRepository.insert(ut);

        Map<String, Object> data = new HashMap<>();
        data.put("token", accessToken);
        data.put("refresh_token", refreshToken);
        data.put("expires_in", ACCESS_TOKEN_EXPIRE_SECONDS);

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getUid());
        userMap.put("phone", user.getPhone());
        userMap.put("nickname", user.getNickname());
        userMap.put("avatar", user.getAvatar());
        userMap.put("role", user.getRole());
        // 经销商用户返回vendorCode
        if (user.getRole() != null && user.getRole() == 2) {
            userMap.put("vendorCode", user.getUsername());
        }
        data.put("user", userMap);

        return data;
    }

    /**
     * 工具方法：为新用户设置密码（供后续注册或管理后台使用）
     */
    public void setUserPassword(User user, String rawPassword) {
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
    }

    // ===================== 第三方登录 =====================

    /**
     * 微信登录
     *
     * @param code 客户端通过微信 SDK 获取的授权码
     * @return 登录结果（token + 用户信息）
     */
    public Map<String, Object> loginByWeChat(String code) {
        try {
            // 验证微信 code 并获取用户信息
            com.pura365.camera.model.auth.WechatUserInfo wechatUser = oAuthService.verifyWeChatCode(code);
            
            // 查找或创建用户
            User user = findOrCreateUserByOAuth(
                AUTH_TYPE_WECHAT, 
                wechatUser.getOpenId(), 
                wechatUser.getUnionId(), 
                wechatUser.getNickname(), 
                wechatUser.getAvatar(), 
                null, 
                wechatUser.getRawResponse()
            );

            return buildLoginResult(user);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("微信登录异常", e);
            throw new RuntimeException("微信登录失败: " + e.getMessage());
        }
    }

    /**
     * Apple 登录
     *
     * @param identityToken 客户端从 Apple 获取的 identity token (JWT)
     * @param userInfo      首次登录时 Apple 返回的用户信息（可能包含 email, fullName）
     * @return 登录结果（token + 用户信息）
     */
    public Map<String, Object> loginByApple(String identityToken, Map<String, String> userInfo) {
        try {
            // 验证 Apple identity token
            Map<String, String> appleUser = oAuthService.verifyAppleIdentityToken(identityToken);
            String openId = appleUser.get("openId");  // Apple 的 sub
            String email = appleUser.get("email");
            String extraInfo = appleUser.get("extraInfo");

            // Apple 首次登录时可能返回用户名称
            String nickname = null;
            if (userInfo != null) {
                String firstName = userInfo.get("firstName");
                String lastName = userInfo.get("lastName");
                if (firstName != null || lastName != null) {
                    nickname = ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
                }
                // 首次登录时 email 可能在 userInfo 中
                if (email == null) {
                    email = userInfo.get("email");
                }
            }

            // 查找或创建用户
            User user = findOrCreateUserByOAuth(AUTH_TYPE_APPLE, openId, null, nickname, null, email, extraInfo);

            return buildLoginResult(user);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Apple 登录异常", e);
            throw new RuntimeException("Apple 登录失败: " + e.getMessage());
        }
    }

    /**
     * Google 登录
     *
     * @param idToken  客户端从 Google 获取的 ID token (JWT)
     * @param platform 平台：ios / android / web
     * @return 登录结果（token + 用户信息）
     */
    public Map<String, Object> loginByGoogle(String idToken, String platform) {
        try {
            // 验证 Google ID token
            Map<String, String> googleUser = oAuthService.verifyGoogleIdToken(idToken, platform);
            String openId = googleUser.get("openId");  // Google 的 sub
            String email = googleUser.get("email");
            String nickname = googleUser.get("nickname");
            String avatar = googleUser.get("avatar");
            String extraInfo = googleUser.get("extraInfo");

            // 查找或创建用户
            User user = findOrCreateUserByOAuth(AUTH_TYPE_GOOGLE, openId, null, nickname, avatar, email, extraInfo);

            return buildLoginResult(user);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google 登录异常", e);
            throw new RuntimeException("Google 登录失败: " + e.getMessage());
        }
    }

    /**
     * Google OAuth Web端登录（使用授权码）
     *
     * @param code Google 回调返回的授权码
     * @return 登录结果（token + 用户信息）
     */
    public Map<String, Object> loginByGoogleCode(String code) {
        try {
            // 使用授权码换取 token 并验证
            Map<String, String> googleUser = oAuthService.exchangeGoogleCodeForToken(code);
            String openId = googleUser.get("openId");  // Google 的 sub
            String email = googleUser.get("email");
            String nickname = googleUser.get("nickname");
            String avatar = googleUser.get("avatar");
            String extraInfo = googleUser.get("extraInfo");

            // 查找或创建用户
            User user = findOrCreateUserByOAuth(AUTH_TYPE_GOOGLE, openId, null, nickname, avatar, email, extraInfo);

            return buildLoginResult(user);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google OAuth 登录异常", e);
            throw new RuntimeException("Google 登录失败: " + e.getMessage());
        }
    }

    /**
     * 通过第三方登录查找或创建用户
     */
    private User findOrCreateUserByOAuth(String authType, String openId, String unionId,
                                          String nickname, String avatar, String email,
                                          String extraInfo) {
        // 查找已有的第三方登录记录
        QueryWrapper<UserAuth> authQw = new QueryWrapper<>();
        authQw.lambda().eq(UserAuth::getAuthType, authType).eq(UserAuth::getOpenId, openId);
        UserAuth existAuth = userAuthRepository.selectOne(authQw);

        if (existAuth != null) {
            // 已经存在绑定，直接返回用户
            User user = userRepository.selectById(existAuth.getUserId());
            if (user == null) {
                throw new RuntimeException("用户不存在");
            }
            // 更新用户信息（如果有新的）
            boolean needUpdate = false;
            if (nickname != null && !nickname.isEmpty() && (user.getNickname() == null || user.getNickname().isEmpty())) {
                user.setNickname(nickname);
                needUpdate = true;
            }
            if (avatar != null && !avatar.isEmpty() && (user.getAvatar() == null || user.getAvatar().isEmpty())) {
                user.setAvatar(avatar);
                needUpdate = true;
            }
            if (needUpdate) {
                userRepository.updateById(user);
            }
            return user;
        }

        // 如果有 email，尝试查找已存在的用户并绑定
        User user = null;
        if (email != null && !email.isEmpty()) {
            QueryWrapper<User> userQw = new QueryWrapper<>();
            userQw.lambda().eq(User::getEmail, email);
            user = userRepository.selectOne(userQw);
        }

        // 创建新用户
        if (user == null) {
            user = new User();
            user.setUid("user_" + System.currentTimeMillis());
            user.setEmail(email);
            user.setNickname(nickname);
            user.setAvatar(avatar);
            // 第三方登录新建用户默认角色也是 1-流通用户
            user.setRole(1);
            userRepository.insert(user);
            log.info("创建新用户: id={}, authType={}, openId={}", user.getId(), authType, openId);
        }

        // 创建第三方登录绑定记录
        UserAuth userAuth = new UserAuth();
        userAuth.setUserId(user.getId());
        userAuth.setAuthType(authType);
        userAuth.setOpenId(openId);
        userAuth.setUnionId(unionId);
        userAuth.setExtraInfo(extraInfo);
        userAuthRepository.insert(userAuth);
        log.info("创建第三方登录绑定: userId={}, authType={}, openId={}", user.getId(), authType, openId);

        return user;
    }
}
