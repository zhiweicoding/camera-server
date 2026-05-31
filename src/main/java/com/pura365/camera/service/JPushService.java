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
import cn.jpush.api.push.model.notification.IosAlert;
import cn.jpush.api.push.model.notification.IosNotification;
import cn.jpush.api.push.model.notification.Notification;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pura365.camera.config.JPushConfig;
import com.pura365.camera.domain.UserPushToken;
import com.pura365.camera.enums.EnableStatus;
import com.pura365.camera.repository.UserPushTokenRepository;
import com.pura365.camera.util.PushProviderUtil;
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
    private final ApnsPushService apnsPushService;
    private final RegionRoutingService regionRoutingService;
    private final String pushProvider;
    private final String iosPushProvider;

    public JPushService(@Autowired(required = false) JPushClient jPushClient,
                        JPushConfig jPushConfig,
                        UserPushTokenRepository userPushTokenRepository,
                        FirebasePushService firebasePushService,
                        ApnsPushService apnsPushService,
                        RegionRoutingService regionRoutingService,
                        @Value("${push.provider:jpush}") String pushProvider,
                        @Value("${push.ios-provider:}") String iosPushProvider) {
        this.jPushClient = jPushClient;
        this.jPushConfig = jPushConfig;
        this.userPushTokenRepository = userPushTokenRepository;
        this.firebasePushService = firebasePushService;
        this.apnsPushService = apnsPushService;
        this.regionRoutingService = regionRoutingService;
        this.pushProvider = pushProvider;
        this.iosPushProvider = iosPushProvider;
    }

    public boolean pushToUser(Long userId, String title, String content, Map<String, String> extras) {
        List<UserPushToken> tokens = getUserPushTokens(userId);
        if (tokens.isEmpty()) {
            logger.warn("用户 {} 没有注册可用推送token", userId);
            return false;
        }
        logger.info("Push token snapshot userId={} tokens={}", userId, summarizeTokens(tokens));

        return pushByTokenProviders(tokens, title, content, extras,
                "userId=" + userId + ", tokenCount=" + tokens.size());
    }

    public boolean pushToUsers(List<Long> userIds, String title, String content, Map<String, String> extras) {
        if (userIds == null || userIds.isEmpty()) {
            return false;
        }

        LambdaQueryWrapper<UserPushToken> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(UserPushToken::getUserId, userIds)
                .eq(UserPushToken::getEnabled, EnableStatus.ENABLED);
        List<UserPushToken> tokens = userPushTokenRepository.selectList(wrapper);

        if (tokens.isEmpty()) {
            logger.warn("用户列表 {} 没有可用推送token", userIds);
            return false;
        }
        logger.info("Push token snapshot userIds={} tokens={}", userIds, summarizeTokens(tokens));

        return pushByTokenProviders(tokens, title, content, extras,
                "userCount=" + userIds.size() + ", tokenCount=" + tokens.size());
    }

    private boolean pushByTokenProviders(List<UserPushToken> tokens,
                                         String title,
                                         String content,
                                         Map<String, String> extras,
                                         String context) {
        if (tokens == null || tokens.isEmpty()) {
            return false;
        }

        List<String> jpushRegistrationIds = new ArrayList<>();
        List<String> fcmTokens = new ArrayList<>();
        List<String> apnsTokens = new ArrayList<>();

        for (UserPushToken token : tokens) {
            if (token == null || !StringUtils.hasText(token.getRegistrationId())) {
                continue;
            }
            String registrationId = token.getRegistrationId().trim();
            String provider = resolveProvider(token);
            if ("fcm".equals(provider)) {
                fcmTokens.add(registrationId);
            } else if ("apns".equals(provider)) {
                apnsTokens.add(registrationId);
            } else {
                jpushRegistrationIds.add(registrationId);
            }
        }

        jpushRegistrationIds = new ArrayList<>(new LinkedHashSet<>(jpushRegistrationIds));
        fcmTokens = new ArrayList<>(new LinkedHashSet<>(fcmTokens));
        apnsTokens = new ArrayList<>(new LinkedHashSet<>(apnsTokens));

        logger.info("Push provider buckets {}, jpushCount={}, firebaseCount={}, apnsCount={}, jpushTargets={}, firebaseTargets={}, apnsTargets={}",
                context,
                jpushRegistrationIds.size(),
                fcmTokens.size(),
                apnsTokens.size(),
                summarizeRegistrationIds(jpushRegistrationIds),
                summarizeRegistrationIds(fcmTokens),
                summarizeRegistrationIds(apnsTokens));

        String pushRegion = regionRoutingService.resolveRegion();
        String routePolicy = resolveRoutePolicy();
        if (regionRoutingService.hasExplicitOverride()) {
            if (regionRoutingService.isChina()) {
                if (!fcmTokens.isEmpty()) {
                    logger.info("Push region policy active region={}, policy={}, skipFirebaseTargets={}",
                            pushRegion, routePolicy, summarizeRegistrationIds(fcmTokens));
                }
                fcmTokens = Collections.emptyList();
            } else {
                if (!jpushRegistrationIds.isEmpty()) {
                    logger.info("Push region policy active region={}, policy={}, skipJPushTargets={}",
                            pushRegion, routePolicy, summarizeRegistrationIds(jpushRegistrationIds));
                }
                jpushRegistrationIds = Collections.emptyList();
            }
        }

        if (jpushRegistrationIds.isEmpty() && fcmTokens.isEmpty() && apnsTokens.isEmpty()) {
            logger.warn("推送失败，当前地区策略过滤后没有可用token: context={}, region={}, policy={}",
                    context, pushRegion, routePolicy);
            return false;
        }

        logger.info("Push route resolved {}, region={}, policy={}, routes={}",
                context, pushRegion, routePolicy, buildRouteSummary(jpushRegistrationIds, fcmTokens, apnsTokens));

        boolean anySuccess = false;
        boolean jpushSuccess = false;
        boolean firebaseSuccess = false;
        boolean apnsSuccess = false;

        if (!jpushRegistrationIds.isEmpty()) {
            logger.info("JPush push start {}, tokenCount={}, apnsProduction={}, targets={}",
                    context, jpushRegistrationIds.size(), jPushConfig.getApnsProduction(),
                    summarizeRegistrationIds(jpushRegistrationIds));
            jpushSuccess = pushToRegistrationIds(jpushRegistrationIds, title, content, extras);
            anySuccess = jpushSuccess || anySuccess;
        }

        if (!fcmTokens.isEmpty()) {
            logger.info("Firebase push start {}, tokenCount={}, targets={}",
                    context, fcmTokens.size(), summarizeRegistrationIds(fcmTokens));
            firebaseSuccess = firebasePushService.pushToTokens(fcmTokens, title, content, extras);
            anySuccess = firebaseSuccess || anySuccess;
        }

        if (!apnsTokens.isEmpty()) {
            logger.info("APNs push start {}, tokenCount={}, targets={}",
                    context, apnsTokens.size(), summarizeRegistrationIds(apnsTokens));
            apnsSuccess = apnsPushService.pushToTokens(apnsTokens, title, content, extras);
            anySuccess = apnsSuccess || anySuccess;
        }

        logger.info("Push route finished {}, region={}, policy={}, result={}",
                context, pushRegion, routePolicy, buildResultSummary(jpushSuccess, firebaseSuccess, apnsSuccess, anySuccess));

        return anySuccess;
    }

    private boolean pushToRegistrationIds(List<String> registrationIds,
                                          String title,
                                          String content,
                                          Map<String, String> extras) {
        if (registrationIds == null || registrationIds.isEmpty()) {
            return false;
        }
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
                                .setAlert(IosAlert.newBuilder()
                                        .setTitleAndBody(title, null, content)
                                        .build())
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
        LambdaQueryWrapper<UserPushToken> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPushToken::getUserId, userId)
                .eq(UserPushToken::getEnabled, EnableStatus.ENABLED);
        return userPushTokenRepository.selectList(wrapper);
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

    private String resolveProvider(UserPushToken token) {
        return PushProviderUtil.resolvePreferredProvider(
                token.getDeviceType(), token.getProvider(), token.getChannel(), pushProvider, iosPushProvider);
    }

    private String maskRegistrationId(String registrationId) {
        return PushProviderUtil.maskToken(registrationId);
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
                + ",provider=" + (StringUtils.hasText(token.getProvider()) ? token.getProvider().trim() : "unknown")
                + ",channel=" + (StringUtils.hasText(token.getChannel()) ? token.getChannel().trim() : "unknown")
                + ",appVersion=" + appVersion
                + ",registrationId=" + maskRegistrationId(token.getRegistrationId());
    }

    private String summarizeRegistrationIds(List<String> registrationIds) {
        if (registrationIds == null || registrationIds.isEmpty()) {
            return "[]";
        }
        return registrationIds.stream()
                .map(this::maskRegistrationId)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private String buildRouteSummary(List<String> jpushRegistrationIds,
                                     List<String> fcmTokens,
                                     List<String> apnsTokens) {
        return "{jpush=" + jpushRegistrationIds.size()
                + ",firebase=" + fcmTokens.size()
                + ",apns=" + apnsTokens.size()
                + ",jpushTargets=" + summarizeRegistrationIds(jpushRegistrationIds)
                + ",firebaseTargets=" + summarizeRegistrationIds(fcmTokens)
                + ",apnsTargets=" + summarizeRegistrationIds(apnsTokens)
                + "}";
    }

    private String buildResultSummary(boolean jpushSuccess,
                                      boolean firebaseSuccess,
                                      boolean apnsSuccess,
                                      boolean anySuccess) {
        return "{jpushSuccess=" + jpushSuccess
                + ",firebaseSuccess=" + firebaseSuccess
                + ",apnsSuccess=" + apnsSuccess
                + ",anySuccess=" + anySuccess
                + "}";
    }

    private String resolveRoutePolicy() {
        if (!regionRoutingService.hasExplicitOverride()) {
            return "token-provider";
        }
        return regionRoutingService.isChina() ? "jpush+apns" : "fcm+apns";
    }
}
