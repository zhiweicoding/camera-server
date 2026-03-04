package com.pura365.camera.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Firebase push sender via FCM HTTP v1 API.
 */
@Service
public class FirebasePushService {

    private static final Logger logger = LoggerFactory.getLogger(FirebasePushService.class);
    private static final String FIREBASE_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final long TOKEN_EXPIRE_BUFFER_MILLIS = 60_000L;
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient();

    @Value("${firebase.credentials-path:}")
    private String credentialsPath;

    @Value("${firebase.project-id:}")
    private String configuredProjectId;

    private final Object tokenLock = new Object();
    private volatile ServiceAccount serviceAccount;
    private volatile String cachedAccessToken;
    private volatile long cachedTokenExpireAt;

    public FirebasePushService(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    public boolean pushToTokens(List<String> tokens, String title, String content, Map<String, String> extras) {
        if (tokens == null || tokens.isEmpty()) {
            return false;
        }

        List<String> normalizedTokens = tokens.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
        if (normalizedTokens.isEmpty()) {
            return false;
        }

        ServiceAccount account = loadServiceAccount();
        if (account == null) {
            return false;
        }

        String projectId = resolveProjectId(account);
        if (!StringUtils.hasText(projectId)) {
            logger.error("Firebase project id is missing. Set firebase.project-id or provide project_id in service account json");
            return false;
        }

        boolean anySuccess = false;
        for (String token : normalizedTokens) {
            if (pushSingle(projectId, token, title, content, extras, account)) {
                anySuccess = true;
            }
        }
        return anySuccess;
    }

    private boolean pushSingle(String projectId,
                               String targetToken,
                               String title,
                               String content,
                               Map<String, String> extras,
                               ServiceAccount account) {
        try {
            String accessToken = getAccessToken(account, false);
            if (!StringUtils.hasText(accessToken)) {
                return false;
            }

            try (Response response = sendFcmRequest(projectId, targetToken, title, content, extras, accessToken)) {
                if (response != null && response.isSuccessful()) {
                    return true;
                }

                int code = response == null ? -1 : response.code();
                if (code == 401 || code == 403) {
                    String refreshedAccessToken = getAccessToken(account, true);
                    if (!StringUtils.hasText(refreshedAccessToken)) {
                        return false;
                    }
                    try (Response retry = sendFcmRequest(projectId, targetToken, title, content, extras, refreshedAccessToken)) {
                        if (retry != null && retry.isSuccessful()) {
                            return true;
                        }
                        String retryBody = retry != null && retry.body() != null ? retry.body().string() : "";
                        int retryCode = retry == null ? -1 : retry.code();
                        logger.warn("Firebase push failed after token refresh code={}, token={}, body={}",
                                retryCode, maskToken(targetToken), retryBody);
                        return false;
                    }
                }

                String body = response != null && response.body() != null ? response.body().string() : "";
                logger.warn("Firebase push failed code={}, token={}, body={}",
                        code, maskToken(targetToken), body);
                return false;
            }
        } catch (Exception e) {
            logger.error("Firebase push exception token={}", maskToken(targetToken), e);
            return false;
        }
    }

    private Response sendFcmRequest(String projectId,
                                    String targetToken,
                                    String title,
                                    String content,
                                    Map<String, String> extras,
                                    String accessToken) throws IOException {
        String payload = buildMessagePayload(targetToken, title, content, extras);
        String url = "https://fcm.googleapis.com/v1/projects/" + projectId + "/messages:send";

        RequestBody requestBody = RequestBody.create(payload, JSON_MEDIA_TYPE);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build();

        return httpClient.newCall(request).execute();
    }

    private String buildMessagePayload(String targetToken,
                                       String title,
                                       String content,
                                       Map<String, String> extras) throws IOException {
        Map<String, Object> message = new HashMap<>();
        message.put("token", targetToken);

        if (StringUtils.hasText(title) || StringUtils.hasText(content)) {
            Map<String, String> notification = new HashMap<>();
            if (StringUtils.hasText(title)) {
                notification.put("title", title);
            }
            if (StringUtils.hasText(content)) {
                notification.put("body", content);
            }
            message.put("notification", notification);
        }

        if (extras != null && !extras.isEmpty()) {
            Map<String, String> data = extras.entrySet().stream()
                    .filter(e -> StringUtils.hasText(e.getKey()) && e.getValue() != null)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
            if (!data.isEmpty()) {
                message.put("data", data);
            }
        }

        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("message", message);
        return objectMapper.writeValueAsString(wrapper);
    }

    private String getAccessToken(ServiceAccount account, boolean forceRefresh) {
        long now = System.currentTimeMillis();
        if (!forceRefresh && StringUtils.hasText(cachedAccessToken)
                && cachedTokenExpireAt - TOKEN_EXPIRE_BUFFER_MILLIS > now) {
            return cachedAccessToken;
        }

        synchronized (tokenLock) {
            now = System.currentTimeMillis();
            if (!forceRefresh && StringUtils.hasText(cachedAccessToken)
                    && cachedTokenExpireAt - TOKEN_EXPIRE_BUFFER_MILLIS > now) {
                return cachedAccessToken;
            }

            AccessTokenResponse tokenResponse = requestAccessToken(account);
            if (tokenResponse == null || !StringUtils.hasText(tokenResponse.accessToken)) {
                return null;
            }

            cachedAccessToken = tokenResponse.accessToken;
            long expiresInSeconds = tokenResponse.expiresIn > 0 ? tokenResponse.expiresIn : 3600L;
            cachedTokenExpireAt = now + expiresInSeconds * 1000L;
            return cachedAccessToken;
        }
    }

    private AccessTokenResponse requestAccessToken(ServiceAccount account) {
        try {
            String assertion = buildJwtAssertion(account);
            RequestBody body = new FormBody.Builder()
                    .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                    .add("assertion", assertion)
                    .build();

            Request request = new Request.Builder()
                    .url(account.tokenUri)
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    logger.error("Firebase token exchange failed code={}, body={}", response.code(), responseBody);
                    return null;
                }

                JsonNode jsonNode = objectMapper.readTree(responseBody);
                AccessTokenResponse tokenResponse = new AccessTokenResponse();
                tokenResponse.accessToken = jsonNode.path("access_token").asText(null);
                tokenResponse.expiresIn = jsonNode.path("expires_in").asLong(3600L);
                return tokenResponse;
            }
        } catch (Exception e) {
            logger.error("Firebase token exchange exception", e);
            return null;
        }
    }

    private String buildJwtAssertion(ServiceAccount account) throws JOSEException {
        long nowSeconds = System.currentTimeMillis() / 1000;

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(account.clientEmail)
                .subject(account.clientEmail)
                .audience(account.tokenUri)
                .claim("scope", FIREBASE_SCOPE)
                .issueTime(new Date(nowSeconds * 1000))
                .expirationTime(new Date((nowSeconds + 3600) * 1000))
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT)
                .build();

        SignedJWT signedJWT = new SignedJWT(header, claims);
        JWSSigner signer = new RSASSASigner(account.privateKey);
        signedJWT.sign(signer);
        return signedJWT.serialize();
    }

    private ServiceAccount loadServiceAccount() {
        if (serviceAccount != null) {
            return serviceAccount;
        }

        synchronized (tokenLock) {
            if (serviceAccount != null) {
                return serviceAccount;
            }

            if (!StringUtils.hasText(credentialsPath)) {
                logger.error("firebase.credentials-path is empty while push.provider=firebase");
                return null;
            }

            Resource resource = resourceLoader.getResource(credentialsPath.trim());
            if (!resource.exists()) {
                logger.error("Firebase credentials file not found: {}", credentialsPath);
                return null;
            }

            try (InputStream in = resource.getInputStream()) {
                JsonNode node = objectMapper.readTree(in);
                String clientEmail = node.path("client_email").asText(null);
                String privateKeyPem = node.path("private_key").asText(null);
                String projectIdFromFile = node.path("project_id").asText(null);
                String tokenUri = node.path("token_uri").asText(DEFAULT_TOKEN_URI);

                if (!StringUtils.hasText(clientEmail) || !StringUtils.hasText(privateKeyPem)) {
                    logger.error("Firebase service account json missing client_email or private_key");
                    return null;
                }

                ServiceAccount loadedAccount = new ServiceAccount();
                loadedAccount.clientEmail = clientEmail.trim();
                loadedAccount.privateKey = parsePrivateKey(privateKeyPem);
                loadedAccount.projectId = StringUtils.hasText(projectIdFromFile) ? projectIdFromFile.trim() : null;
                loadedAccount.tokenUri = StringUtils.hasText(tokenUri) ? tokenUri.trim() : DEFAULT_TOKEN_URI;

                serviceAccount = loadedAccount;
                return serviceAccount;
            } catch (Exception e) {
                logger.error("Failed to load firebase service account from {}", credentialsPath, e);
                return null;
            }
        }
    }

    private RSAPrivateKey parsePrivateKey(String privateKeyPem) throws Exception {
        String normalized = privateKeyPem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(normalized);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }

    private String resolveProjectId(ServiceAccount account) {
        if (StringUtils.hasText(configuredProjectId)) {
            return configuredProjectId.trim();
        }
        return account.projectId;
    }

    private String maskToken(String token) {
        if (!StringUtils.hasText(token)) {
            return "EMPTY";
        }
        String value = token.trim();
        if (value.length() <= 8) {
            return value;
        }
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }

    private static class ServiceAccount {
        private String clientEmail;
        private RSAPrivateKey privateKey;
        private String projectId;
        private String tokenUri;
    }

    private static class AccessTokenResponse {
        private String accessToken;
        private long expiresIn;
    }
}
