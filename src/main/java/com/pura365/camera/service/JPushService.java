package com.pura365.camera.service;

import cn.jiguang.common.ClientConfig;
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
import com.pura365.camera.enums.EnableStatus;
import com.pura365.camera.config.JPushConfig;
import com.pura365.camera.domain.UserPushToken;
import com.pura365.camera.repository.UserPushTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 极光推送服务
 */
@Service
public class JPushService {

    private static final Logger logger = LoggerFactory.getLogger(JPushService.class);

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
     *
     * @param userId  用户ID
     * @param title   推送标题
     * @param content 推送内容
     * @param extras  附加数据
     * @return 是否推送成功
     */
    public boolean pushToUser(Long userId, String title, String content, Map<String, String> extras) {
        // 获取用户的所有推送token
        List<UserPushToken> tokens = getUserPushTokens(userId);
        if (tokens.isEmpty()) {
            logger.warn("用户 {} 没有注册推送token", userId);
            return false;
        }

        List<String> registrationIds = tokens.stream()
                .map(UserPushToken::getRegistrationId)
                .collect(Collectors.toList());

        return pushToRegistrationIds(registrationIds, title, content, extras);
    }

    /**
     * 推送消息给多个用户
     *
     * @param userIds 用户ID列表
     * @param title   推送标题
     * @param content 推送内容
     * @param extras  附加数据
     * @return 是否推送成功
     */
    public boolean pushToUsers(List<Long> userIds, String title, String content, Map<String, String> extras) {
        if (userIds == null || userIds.isEmpty()) {
            return false;
        }

        // 获取所有用户的推送token
        LambdaQueryWrapper<UserPushToken> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(UserPushToken::getUserId, userIds)
               .eq(UserPushToken::getEnabled, EnableStatus.ENABLED);
        List<UserPushToken> tokens = userPushTokenRepository.selectList(wrapper);

        if (tokens.isEmpty()) {
            logger.warn("用户列表 {} 没有可用的推送token", userIds);
            return false;
        }

        List<String> registrationIds = tokens.stream()
                .map(UserPushToken::getRegistrationId)
                .collect(Collectors.toList());

        return pushToRegistrationIds(registrationIds, title, content, extras);
    }

    /**
     * 推送消息到指定Registration ID列表
     *
     * @param registrationIds Registration ID列表
     * @param title           推送标题
     * @param content         推送内容
     * @param extras          附加数据
     * @return 是否推送成功
     */
    private boolean pushToRegistrationIds(List<String> registrationIds, String title, String content, Map<String, String> extras) {
        try {
            PushPayload payload = buildPushPayload(registrationIds, title, content, extras);
            PushResult result = jPushClient.sendPush(payload);
            logger.info("极光推送成功: msgId={}, sendNo={}", result.msg_id, result.sendno);
            return true;
        } catch (APIConnectionException e) {
            logger.error("极光推送连接异常", e);
            return false;
        } catch (APIRequestException e) {
            logger.error("极光推送请求异常: status={}, errorCode={}, errorMessage={}", 
                    e.getStatus(), e.getErrorCode(), e.getErrorMessage(), e);
            return false;
        }
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
}
