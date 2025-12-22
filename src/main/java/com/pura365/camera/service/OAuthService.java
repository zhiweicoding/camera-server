package com.pura365.camera.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.pura365.camera.config.OAuthConfig;
import com.pura365.camera.model.auth.WechatUserInfo;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.security.interfaces.RSAPublicKey;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 第三方 OAuth 认证服务
 * 负责验证微信/Apple/Google 的登录凭证
 */
@Service
public class OAuthService {

    private static final Logger log = LoggerFactory.getLogger(OAuthService.class);

    private static final String WECHAT_ACCESS_TOKEN_URL = "https://api.weixin.qq.com/sns/oauth2/access_token";
    private static final String WECHAT_USERINFO_URL = "https://api.weixin.qq.com/sns/userinfo";
    private static final String APPLE_JWKS_URL = "https://appleid.apple.com/auth/keys";
    private static final String GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";

    @Autowired
    private OAuthConfig oAuthConfig;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 缓存 Apple/Google JWKS，避免频繁请求
    private JWKSet appleJwkSet;
    private long appleJwkSetExpire = 0;
    private JWKSet googleJwkSet;
    private long googleJwkSetExpire = 0;
    private static final long JWKS_CACHE_DURATION_MS = 3600 * 1000; // 1 小时

    /**
     * 微信登录：使用 code 换取 access_token 和 openid
     *
     * @param code 客户端通过微信 SDK 获取的授权码
     * @return 微信用户信息
     * @throws Exception 验证失败或网络异常
     */
    public WechatUserInfo verifyWeChatCode(String code) throws Exception {
        String appId = oAuthConfig.getWechat().getAppId();
        String appSecret = oAuthConfig.getWechat().getAppSecret();

        if (appId == null || appSecret == null || appId.startsWith("YOUR_")) {
            throw new RuntimeException("微信登录配置未设置，请联系管理员");
        }

        // Step 1: 用 code 换取 access_token
        String tokenUrl = String.format(
                "%s?appid=%s&secret=%s&code=%s&grant_type=authorization_code",
                WECHAT_ACCESS_TOKEN_URL, appId, appSecret, code
        );

        Request request = new Request.Builder().url(tokenUrl).get().build();
        String tokenResp;
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new RuntimeException("微信授权请求失败");
            }
            tokenResp = response.body().string();
        }

        JsonNode tokenJson = objectMapper.readTree(tokenResp);
        if (tokenJson.has("errcode") && tokenJson.get("errcode").asInt() != 0) {
            String errMsg = tokenJson.has("errmsg") ? tokenJson.get("errmsg").asText() : "未知错误";
            log.warn("微信登录获取 token 失败: {}", errMsg);
            throw new RuntimeException("微信授权失败: " + errMsg);
        }

        String accessToken = tokenJson.get("access_token").asText();
        String openId = tokenJson.get("openid").asText();
        String unionId = tokenJson.has("unionid") ? tokenJson.get("unionid").asText() : null;

        // Step 2: 获取用户信息
        String userInfoUrl = String.format(
                "%s?access_token=%s&openid=%s",
                WECHAT_USERINFO_URL, accessToken, openId
        );

        request = new Request.Builder().url(userInfoUrl).get().build();
        String userInfoResp;
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new RuntimeException("获取微信用户信息失败");
            }
            userInfoResp = response.body().string();
        }

        JsonNode userInfoJson = objectMapper.readTree(userInfoResp);
        if (userInfoJson.has("errcode") && userInfoJson.get("errcode").asInt() != 0) {
            log.warn("获取微信用户信息失败: {}", userInfoJson.get("errmsg").asText());
            // 即使获取用户信息失败，也可以用 openid 登录
        }

        // 封装为标准的用户信息对象
        WechatUserInfo userInfo = new WechatUserInfo();
        userInfo.setOpenId(openId);
        userInfo.setUnionId(unionId);
        userInfo.setNickname(userInfoJson.has("nickname") ? userInfoJson.get("nickname").asText() : null);
        userInfo.setAvatar(userInfoJson.has("headimgurl") ? userInfoJson.get("headimgurl").asText() : null);
        userInfo.setGender(userInfoJson.has("sex") ? userInfoJson.get("sex").asInt() : null);
        userInfo.setCountry(userInfoJson.has("country") ? userInfoJson.get("country").asText() : null);
        userInfo.setProvince(userInfoJson.has("province") ? userInfoJson.get("province").asText() : null);
        userInfo.setCity(userInfoJson.has("city") ? userInfoJson.get("city").asText() : null);
        userInfo.setRawResponse(userInfoResp);

        return userInfo;
    }

    /**
     * Apple 登录：验证 identityToken (JWT)
     *
     * @param identityToken 客户端从 Apple 获取的 identity token (JWT)
     * @return 包含 sub (用户唯一标识), email 等信息
     */
    public Map<String, String> verifyAppleIdentityToken(String identityToken) throws Exception {
        String clientId = oAuthConfig.getApple().getClientId();

        if (clientId == null || clientId.startsWith("YOUR_")) {
            throw new RuntimeException("Apple 登录配置未设置，请联系管理员");
        }

        // 解析 JWT
        SignedJWT signedJWT = SignedJWT.parse(identityToken);
        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

        // 验证 issuer
        String issuer = claims.getIssuer();
        if (!"https://appleid.apple.com".equals(issuer)) {
            throw new RuntimeException("Apple token issuer 无效");
        }

        // 验证 audience (client_id)
        List<String> audience = claims.getAudience();
        if (audience == null || !audience.contains(clientId)) {
            throw new RuntimeException("Apple token audience 无效");
        }

        // 验证过期时间
        Date expiration = claims.getExpirationTime();
        if (expiration == null || expiration.before(new Date())) {
            throw new RuntimeException("Apple token 已过期");
        }

        // 获取 Apple 公钥并验证签名
        JWKSet jwkSet = getAppleJwkSet();
        String kid = signedJWT.getHeader().getKeyID();
        JWK jwk = jwkSet.getKeyByKeyId(kid);
        if (jwk == null) {
            // 尝试刷新 JWKS
            appleJwkSetExpire = 0;
            jwkSet = getAppleJwkSet();
            jwk = jwkSet.getKeyByKeyId(kid);
            if (jwk == null) {
                throw new RuntimeException("Apple token 签名密钥未找到");
            }
        }

        RSAPublicKey publicKey = ((RSAKey) jwk).toRSAPublicKey();
        com.nimbusds.jose.crypto.RSASSAVerifier verifier = new com.nimbusds.jose.crypto.RSASSAVerifier(publicKey);
        if (!signedJWT.verify(verifier)) {
            throw new RuntimeException("Apple token 签名验证失败");
        }

        // 提取用户信息
        String sub = claims.getSubject();  // 用户唯一标识
        String email = claims.getStringClaim("email");
        Boolean emailVerified = claims.getBooleanClaim("email_verified");

        Map<String, String> result = new HashMap<>();
        result.put("openId", sub);
        result.put("email", email);
        result.put("emailVerified", emailVerified != null ? emailVerified.toString() : null);
        result.put("extraInfo", claims.toJSONObject().toString());

        return result;
    }

    /**
     * Google 登录：验证 idToken (JWT)
     *
     * @param idToken    客户端从 Google 获取的 ID token (JWT)
     * @param platform   平台：ios / android / web
     * @return 包含 sub (用户唯一标识), email, name, picture 等信息
     */
    public Map<String, String> verifyGoogleIdToken(String idToken, String platform) throws Exception {
        String expectedClientId = getGoogleClientId(platform);

        if (expectedClientId == null || expectedClientId.startsWith("YOUR_")) {
            throw new RuntimeException("Google 登录配置未设置，请联系管理员");
        }

        // 解析 JWT
        SignedJWT signedJWT = SignedJWT.parse(idToken);
        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

        // 验证 issuer
        String issuer = claims.getIssuer();
        if (!"https://accounts.google.com".equals(issuer) && !"accounts.google.com".equals(issuer)) {
            throw new RuntimeException("Google token issuer 无效");
        }

        // 验证 audience (client_id) - 允许匹配任一配置的 client_id
        List<String> audience = claims.getAudience();
        if (audience == null || !isValidGoogleAudience(audience)) {
            throw new RuntimeException("Google token audience 无效");
        }

        // 验证过期时间
        Date expiration = claims.getExpirationTime();
        if (expiration == null || expiration.before(new Date())) {
            throw new RuntimeException("Google token 已过期");
        }

        // 获取 Google 公钥并验证签名
        JWKSet jwkSet = getGoogleJwkSet();
        String kid = signedJWT.getHeader().getKeyID();
        JWK jwk = jwkSet.getKeyByKeyId(kid);
        if (jwk == null) {
            // 尝试刷新 JWKS
            googleJwkSetExpire = 0;
            jwkSet = getGoogleJwkSet();
            jwk = jwkSet.getKeyByKeyId(kid);
            if (jwk == null) {
                throw new RuntimeException("Google token 签名密钥未找到");
            }
        }

        // 验证签名算法
        JWSAlgorithm algorithm = signedJWT.getHeader().getAlgorithm();
        if (!JWSAlgorithm.RS256.equals(algorithm)) {
            throw new RuntimeException("Google token 签名算法不支持");
        }

        RSAPublicKey publicKey = ((RSAKey) jwk).toRSAPublicKey();
        com.nimbusds.jose.crypto.RSASSAVerifier verifier = new com.nimbusds.jose.crypto.RSASSAVerifier(publicKey);
        if (!signedJWT.verify(verifier)) {
            throw new RuntimeException("Google token 签名验证失败");
        }

        // 提取用户信息
        String sub = claims.getSubject();  // 用户唯一标识
        String email = claims.getStringClaim("email");
        Boolean emailVerified = claims.getBooleanClaim("email_verified");
        String name = claims.getStringClaim("name");
        String picture = claims.getStringClaim("picture");

        Map<String, String> result = new HashMap<>();
        result.put("openId", sub);
        result.put("email", email);
        result.put("emailVerified", emailVerified != null ? emailVerified.toString() : null);
        result.put("nickname", name);
        result.put("avatar", picture);
        result.put("extraInfo", claims.toJSONObject().toString());

        return result;
    }

    private synchronized JWKSet getAppleJwkSet() throws Exception {
        long now = System.currentTimeMillis();
        if (appleJwkSet == null || now > appleJwkSetExpire) {
            log.info("正在获取 Apple JWKS...");
            appleJwkSet = JWKSet.load(new URL(APPLE_JWKS_URL));
            appleJwkSetExpire = now + JWKS_CACHE_DURATION_MS;
        }
        return appleJwkSet;
    }

    private synchronized JWKSet getGoogleJwkSet() throws Exception {
        long now = System.currentTimeMillis();
        if (googleJwkSet == null || now > googleJwkSetExpire) {
            log.info("正在获取 Google JWKS...");
            googleJwkSet = JWKSet.load(new URL(GOOGLE_JWKS_URL));
            googleJwkSetExpire = now + JWKS_CACHE_DURATION_MS;
        }
        return googleJwkSet;
    }

    private String getGoogleClientId(String platform) {
        if ("ios".equalsIgnoreCase(platform)) {
            return oAuthConfig.getGoogle().getClientIdIos();
        } else if ("android".equalsIgnoreCase(platform)) {
            return oAuthConfig.getGoogle().getClientIdAndroid();
        } else {
            return oAuthConfig.getGoogle().getClientIdWeb();
        }
    }

    private boolean isValidGoogleAudience(List<String> audience) {
        OAuthConfig.GoogleConfig gc = oAuthConfig.getGoogle();
        Set<String> validIds = new HashSet<>();
        if (gc.getClientIdIos() != null && !gc.getClientIdIos().startsWith("YOUR_")) {
            validIds.add(gc.getClientIdIos());
        }
        if (gc.getClientIdAndroid() != null && !gc.getClientIdAndroid().startsWith("YOUR_")) {
            validIds.add(gc.getClientIdAndroid());
        }
        if (gc.getClientIdWeb() != null && !gc.getClientIdWeb().startsWith("YOUR_")) {
            validIds.add(gc.getClientIdWeb());
        }
        for (String aud : audience) {
            if (validIds.contains(aud)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Google OAuth Web端：使用授权码换取 ID Token
     *
     * @param code Google 回调返回的授权码
     * @return 包含 openId, email, nickname, avatar 等信息
     */
    public Map<String, String> exchangeGoogleCodeForToken(String code) throws Exception {
        OAuthConfig.GoogleConfig gc = oAuthConfig.getGoogle();
        String clientId = gc.getClientIdWeb();
        String clientSecret = gc.getClientSecretWeb();
        String redirectUri = gc.getRedirectUri();

        if (clientId == null || clientId.startsWith("YOUR_")) {
            throw new RuntimeException("Google Web Client ID 未配置");
        }
        if (clientSecret == null || clientSecret.startsWith("YOUR_")) {
            throw new RuntimeException("Google Web Client Secret 未配置");
        }
        if (redirectUri == null || redirectUri.isEmpty()) {
            throw new RuntimeException("Google Redirect URI 未配置");
        }

        // 构建请求体
        String requestBody = String.format(
                "code=%s&client_id=%s&client_secret=%s&redirect_uri=%s&grant_type=authorization_code",
                java.net.URLEncoder.encode(code, "UTF-8"),
                java.net.URLEncoder.encode(clientId, "UTF-8"),
                java.net.URLEncoder.encode(clientSecret, "UTF-8"),
                java.net.URLEncoder.encode(redirectUri, "UTF-8")
        );

        okhttp3.RequestBody body = okhttp3.RequestBody.create(
                requestBody,
                okhttp3.MediaType.parse("application/x-www-form-urlencoded")
        );

        Request request = new Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(body)
                .build();

        String tokenResp;
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.error("Google token exchange failed: {}", response.code());
                throw new RuntimeException("Google 授权请求失败");
            }
            tokenResp = response.body().string();
        }

        JsonNode tokenJson = objectMapper.readTree(tokenResp);
        if (tokenJson.has("error")) {
            String error = tokenJson.get("error").asText();
            String errorDesc = tokenJson.has("error_description") 
                    ? tokenJson.get("error_description").asText() 
                    : "未知错误";
            log.warn("Google token exchange error: {} - {}", error, errorDesc);
            throw new RuntimeException("Google 授权失败: " + errorDesc);
        }

        String idToken = tokenJson.get("id_token").asText();
        
        // 验证并解析 ID Token
        return verifyGoogleIdToken(idToken, "web");
    }
}
