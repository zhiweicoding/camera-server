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
 * 极光推送服务
 */
@Service
public class JPushService {

    private static final Logger logger = LoggerFactory.getLogger(JPushService.class);
    private static final int MAX_REGISTRATION_IDS_PER_BATCH = 500;

    private final JPushClient jPushClient;
    private final JPushConfig jPushConfig;
    private final UserPushTokenRepository userPushTokenRepository;

    public JPushService(JPushClient jPushClient,
                        JPushConfig jPushConfig,
                        UserPushTokenRepository userPushTokenRepository) {
        this.jPushClient = jPushClient;
        this.jPushConfig = jPushConfig;
        this.userPushTokenRepository = userPushTokenRepository;
    }

    /**
     * 推送消息给单个用户
     */
    public boolean pushToUser(Long userId, String title, String content, Map<String, String> extras) {
        List<UserPushToken> tokens = getUserPushTokens(userId);
        if (tokens.isEmpty()) {
            logger.warn("用户 {} 没有注册可用推送token", userId);
            return false;
        }
        logger.info("JPush token snapshot userId={} tokens={}", userId, summarizeTokens(tokens));

        List<String> registrationIds = extractRegistrationIds(tokens);
        if (registrationIds.isEmpty()) {
            logger.warn("用户 {} 的推送token全部为空或无效", userId);
            return false;
        }

        logger.info("极光推送开始 userId={}, tokenCount={}, apnsProduction={}",
                userId, registrationIds.size(), jPushConfig.getApnsProduction());
        return pushToRegistrationIds(registrationIds, title, content, extras);
    }

    /**
     * 推送消息给多个用户
     */
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
        logger.info("JPush token snapshot userIds={} tokens={}", userIds, summarizeTokens(tokens));

        List<String> registrationIds = extractRegistrationIds(tokens);
        if (registrationIds.isEmpty()) {
            logger.warn("用户列表 {} 的推送token全部为空或无效", userIds);
            return false;
        }

        logger.info("极光推送开始 userCount={}, tokenCount={}, apnsProduction={}",
                userIds.size(), registrationIds.size(), jPushConfig.getApnsProduction());
        return pushToRegistrationIds(registrationIds, title, content, extras);
    }

    /**
     * 推送消息到指定Registration ID列表
     */
    private boolean pushToRegistrationIds(List<String> registrationIds, String title, String content, Map<String, String> extras) {
        if (registrationIds == null || registrationIds.isEmpty()) {
            return false;
        }

        boolean anySuccess = false;
        List<List<String>> batches = partition(registrationIds, MAX_REGISTRATION_IDS_PER_BATCH);
        for (List<String> batch : batches) {
            try {
                PushPayload payload = buildPushPayload(batch, title, content, extras);
                PushResult result = jPushClient.sendPush(payload);
                logger.info("极光推送成功 msgId={}, sendNo={}, targetCount={}",
                        result.msg_id, result.sendno, batch.size());
                anySuccess = true;
            } catch (APIConnectionException e) {
                logger.error("极光推送连接异常 targetCount={}", batch.size(), e);
            } catch (APIRequestException e) {
                logger.error("极光推送请求异常 status={}, errorCode={}, errorMessage={}, targetCount={}, sampleRegistrationId={}",
                        e.getStatus(), e.getErrorCode(), e.getErrorMessage(), batch.size(), maskRegistrationId(batch.get(0)), e);
                // 防止单个无效registrationId拖垮整批推送，失败时回退到单条发送
                if (batch.size() > 1) {
                    anySuccess = pushOneByOne(batch, title, content, extras) || anySuccess;
                }
            }
        }

        return anySuccess;
    }

    /**
     * 批量失败时，逐条发送定位无效token并尽可能送达
     */
    private boolean pushOneByOne(List<String> registrationIds, String title, String content, Map<String, String> extras) {
        boolean anySuccess = false;
        for (String registrationId : registrationIds) {
            try {
                PushPayload payload = buildPushPayload(Collections.singletonList(registrationId), title, content, extras);
                PushResult result = jPushClient.sendPush(payload);
                logger.info("极光单条推送成功 msgId={}, sendNo={}, registrationId={}",
                        result.msg_id, result.sendno, maskRegistrationId(registrationId));
                anySuccess = true;
            } catch (APIConnectionException e) {
                logger.error("极光单条推送连接异常 registrationId={}", maskRegistrationId(registrationId), e);
            } catch (APIRequestException e) {
                logger.error("极光单条推送失败 status={}, errorCode={}, errorMessage={}, registrationId={}",
                        e.getStatus(), e.getErrorCode(), e.getErrorMessage(), maskRegistrationId(registrationId), e);
            }
        }
        return anySuccess;
    }

    /**
     * 构建推送载荷
     */
    private PushPayload buildPushPayload(List<String> registrationIds, String title, String content, Map<String, String> extras) {
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

    /**
     * 获取用户的推送token列表
     */
    private List<UserPushToken> getUserPushTokens(Long userId) {
        LambdaQueryWrapper<UserPushToken> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPushToken::getUserId, userId)
                .eq(UserPushToken::getEnabled, EnableStatus.ENABLED);
        return userPushTokenRepository.selectList(wrapper);
    }

    private List<String> extractRegistrationIds(List<UserPushToken> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return Collections.emptyList();
        }
        return tokens.stream()
                .map(UserPushToken::getRegistrationId)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
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
