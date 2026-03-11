package com.pura365.camera.service;

import cn.jiguang.common.resp.APIConnectionException;
import cn.jiguang.common.resp.APIRequestException;
import cn.jpush.api.JPushClient;
import cn.jpush.api.push.PushResult;
import cn.jpush.api.push.model.Message;
import cn.jpush.api.push.model.Options;
import cn.jpush.api.push.model.Platform;
import cn.jpush.api.push.model.PushPayload;
import cn.jpush.api.push.model.audience.Audience;
import cn.jpush.api.push.model.notification.AndroidNotification;
import cn.jpush.api.push.model.notification.IosNotification;
import cn.jpush.api.push.model.notification.Notification;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pura365.camera.config.JPushConfig;
import com.pura365.camera.domain.UserPushToken;
import com.pura365.camera.enums.EnableStatus;
import com.pura365.camera.repository.UserPushTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Push service. Keeps legacy class name for compatibility.
 */
@Service
public class JPushService {

    private static final Logger logger = LoggerFactory.getLogger(JPushService.class);
    private static final int MAX_REGISTRATION_IDS_PER_BATCH = 500;

    private final JPushClient jPushClient;
    private final JPushConfig jPushConfig;
    private final UserPushTokenRepository userPushTokenRepository;
    private final FirebasePushService firebasePushService;
    private final String pushProvider;

    public JPushService(@Autowired(required = false) JPushClient jPushClient,
                        JPushConfig jPushConfig,
                        UserPushTokenRepository userPushTokenRepository,
                        FirebasePushService firebasePushService,
                        @Value("${push.provider:jpush}") String pushProvider) {
        this.jPushClient = jPushClient;
        this.jPushConfig = jPushConfig;
        this.userPushTokenRepository = userPushTokenRepository;
        this.firebasePushService = firebasePushService;
        this.pushProvider = pushProvider;
    }

    public boolean pushToUser(Long userId, String title, String content, Map<String, String> extras) {
        logger.info("Push dispatch prepare source=message_service userId={} title={} contentLength={}",
                userId,
                title,
                content == null ? 0 : content.length());
        List<UserPushToken> tokens = getUserPushTokens(userId);
        if (tokens.isEmpty()) {
            logger.error("User {} has no enabled push tokens, skip push", userId);
            return false;
        }
        logger.info("Push token snapshot userId={} tokens={}", userId, summarizeTokens(tokens));

        List<String> registrationIds = extractRegistrationIds(tokens);
        if (registrationIds.isEmpty()) {
            logger.warn("User {} has no valid registration_id after filtering", userId);
            return false;
        }

        return pushByProvider(registrationIds, title, content, extras,
                "userId=" + userId + ", tokenCount=" + registrationIds.size());
    }

    public boolean pushToUsers(List<Long> userIds, String title, String content, Map<String, String> extras) {
        if (userIds == null || userIds.isEmpty()) {
            return false;
        }

        logger.info("Push token source=user_push_token_table queryType=batch users={}", userIds);
        LambdaQueryWrapper<UserPushToken> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(UserPushToken::getUserId, userIds)
                .eq(UserPushToken::getEnabled, EnableStatus.ENABLED);
        List<UserPushToken> tokens = userPushTokenRepository.selectList(wrapper);

        if (tokens.isEmpty()) {
            logger.warn("鐢ㄦ埛鍒楄〃 {} 娌℃湁鍙敤鎺ㄩ€乼oken", userIds);
            return false;
        }
        logger.info("Push token snapshot userIds={} tokens={}", userIds, summarizeTokens(tokens));

        List<String> registrationIds = extractRegistrationIds(tokens);
        if (registrationIds.isEmpty()) {
            logger.warn("Users {} have no valid registration_id after filtering", userIds);
            return false;
        }

        return pushByProvider(registrationIds, title, content, extras,
                "userCount=" + userIds.size() + ", tokenCount=" + registrationIds.size());
    }

    private boolean pushByProvider(List<String> registrationIds,
                                   String title,
                                   String content,
                                   Map<String, String> extras,
                                   String context) {
        String provider = normalizeProvider(pushProvider);
        boolean useHybrid = isHybridProvider(provider);
        boolean useFirebase = useHybrid || isFirebaseProvider(provider);
        boolean useJPush = useHybrid || isJPushProvider(provider);

        if (!useFirebase && !useJPush) {
            logger.warn("Unknown push.provider={}, fallback to jpush", pushProvider);
            useJPush = true;
        }

        boolean jPushSuccess = false;
        if (useJPush) {
            logger.info("JPush push start {}, provider={}, apnsProduction={}",
                    context, provider, jPushConfig.getApnsProduction());
            jPushSuccess = pushToRegistrationIds(registrationIds, title, content, extras);
        }

        boolean firebaseSuccess = false;
        if (useFirebase) {
            logger.info("Firebase push start {}, provider={}", context, provider);
            firebaseSuccess = firebasePushService.pushToTokens(registrationIds, title, content, extras);
        }

        boolean success = jPushSuccess || firebaseSuccess;
        logger.info("Push dispatch finish {}, provider={}, jpushSuccess={}, firebaseSuccess={}, overallSuccess={}",
                context, provider, jPushSuccess, firebaseSuccess, success);
        return success;
    }

    private boolean pushToRegistrationIds(List<String> registrationIds,
                                          String title,
                                          String content,
                                          Map<String, String> extras) {
        if (registrationIds == null || registrationIds.isEmpty()) {
            return false;
        }
        logger.info("Push dispatch target registrationIdsCount={} samples={}",
                registrationIds.size(),
                summarizeRegistrationIds(registrationIds));
        if (jPushClient == null) {
            logger.error("JPush client is not initialized while provider={}.", pushProvider);
            return false;
        }

        boolean anySuccess = false;
        List<List<String>> batches = partition(registrationIds, MAX_REGISTRATION_IDS_PER_BATCH);
        for (List<String> batch : batches) {
            try {
                PushPayload payload = buildPushPayload(batch, title, content, extras);
                PushResult result = jPushClient.sendPush(payload);
                logger.info("JPush success msgId={}, sendNo={}, targetCount={}",
                        result.msg_id, result.sendno, batch.size());
                anySuccess = true;
            } catch (APIConnectionException e) {
                logger.error("JPush connection failed targetCount={}", batch.size(), e);
            } catch (APIRequestException e) {
                logger.error("JPush request failed status={}, errorCode={}, errorMessage={}, targetCount={}, sampleRegistrationId={}",
                        e.getStatus(), e.getErrorCode(), e.getErrorMessage(), batch.size(), maskRegistrationId(batch.get(0)), e);
                if (batch.size() > 1) {
                    anySuccess = pushOneByOne(batch, title, content, extras) || anySuccess;
                }
            }
        }

        return anySuccess;
    }

    private boolean pushOneByOne(List<String> registrationIds,
                                 String title,
                                 String content,
                                 Map<String, String> extras) {
        boolean anySuccess = false;
        for (String registrationId : registrationIds) {
            try {
                PushPayload payload = buildPushPayload(Collections.singletonList(registrationId), title, content, extras);
                PushResult result = jPushClient.sendPush(payload);
                logger.info("JPush single success msgId={}, sendNo={}, registrationId={}",
                        result.msg_id, result.sendno, maskRegistrationId(registrationId));
                anySuccess = true;
            } catch (APIConnectionException e) {
                logger.error("JPush single connection failed registrationId={}", maskRegistrationId(registrationId), e);
            } catch (APIRequestException e) {
                logger.error("JPush single request failed status={}, errorCode={}, errorMessage={}, registrationId={}",
                        e.getStatus(), e.getErrorCode(), e.getErrorMessage(), maskRegistrationId(registrationId), e);
            }
        }
        return anySuccess;
    }

    private PushPayload buildPushPayload(List<String> registrationIds,
                                         String title,
                                         String content,
                                         Map<String, String> extras) {
        if (extras == null) {
            extras = new HashMap<>();
        }

        return PushPayload.newBuilder()
                .setPlatform(Platform.android_ios())
                .setAudience(Audience.registrationId(registrationIds))
                .setNotification(Notification.newBuilder()
                        .setAlert(content)
                        .addPlatformNotification(AndroidNotification.newBuilder()
                                .setTitle(title)
                                .setAlert(content)
                                .addExtras(extras)
                                .build())
                        .addPlatformNotification(IosNotification.newBuilder()
                                .setAlert(content)
                                .setSound("default")
                                .addExtras(extras)
                                .build())
                        .build())
                .setMessage(Message.newBuilder()
                        .setTitle(title)
                        .setMsgContent(content)
                        .addExtras(extras)
                        .build())
                .setOptions(Options.newBuilder()
                        .setApnsProduction(jPushConfig.getApnsProduction())
                        .build())
                .build();
    }

    private List<UserPushToken> getUserPushTokens(Long userId) {
        logger.info("Push token source=user_push_token_table queryType=single userId={}", userId);
        LambdaQueryWrapper<UserPushToken> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPushToken::getUserId, userId)
                .eq(UserPushToken::getEnabled, EnableStatus.ENABLED);
        List<UserPushToken> tokens = userPushTokenRepository.selectList(wrapper);
        logger.info("Push token source=user_push_token_table queryResult userId={} enabledTokenCount={}",
                userId, tokens == null ? 0 : tokens.size());
        return tokens;
    }

    private List<String> extractRegistrationIds(List<UserPushToken> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> registrationIds = tokens.stream()
                .map(UserPushToken::getRegistrationId)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
        logger.info("Push token extract summary rawTokenCount={} validRegistrationIdCount={} samples={}",
                tokens.size(),
                registrationIds.size(),
                summarizeRegistrationIds(registrationIds));
        return registrationIds;
    }

    private List<List<String>> partition(List<String> registrationIds, int batchSize) {
        if (registrationIds == null || registrationIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> deduped = new ArrayList<>(new LinkedHashSet<>(registrationIds));
        List<List<String>> result = new ArrayList<>();
        for (int i = 0; i < deduped.size(); i += batchSize) {
            int end = Math.min(i + batchSize, deduped.size());
            result.add(deduped.subList(i, end));
        }
        return result;
    }

    private String normalizeProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            return "jpush";
        }
        return provider.trim().toLowerCase();
    }

    private boolean isFirebaseProvider(String provider) {
        return "firebase".equals(provider) || "fcm".equals(provider);
    }

    private boolean isJPushProvider(String provider) {
        return "jpush".equals(provider);
    }

    private boolean isHybridProvider(String provider) {
        return "hybrid".equals(provider) || "both".equals(provider) || "all".equals(provider);
    }

    private String maskRegistrationId(String registrationId) {
        if (!StringUtils.hasText(registrationId)) {
            return "EMPTY";
        }
        String value = registrationId.trim();
        if (value.length() <= 8) {
            return value;
        }
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }

    private String summarizeRegistrationIds(List<String> registrationIds) {
        if (registrationIds == null || registrationIds.isEmpty()) {
            return "[]";
        }
        return registrationIds.stream()
                .limit(5)
                .map(this::maskRegistrationId)
                .collect(Collectors.joining(", ", "[", registrationIds.size() > 5 ? ", ...]" : "]"));
    }

    private String summarizeTokens(List<UserPushToken> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return "[]";
        }
        return tokens.stream()
                .map(this::formatTokenForLog)
                .collect(Collectors.joining("; ", "[", "]"));
    }

    private String formatTokenForLog(UserPushToken token) {
        String tokenId = token.getId() == null ? "null" : token.getId().toString();
        String userId = token.getUserId() == null ? "null" : token.getUserId().toString();
        String deviceType = StringUtils.hasText(token.getDeviceType()) ? token.getDeviceType().trim() : "unknown";
        String appVersion = StringUtils.hasText(token.getAppVersion()) ? token.getAppVersion().trim() : "unknown";

        return "id=" + tokenId
                + ",userId=" + userId
                + ",deviceType=" + deviceType
                + ",appVersion=" + appVersion
                + ",registrationId=" + maskRegistrationId(token.getRegistrationId());
    }
}

