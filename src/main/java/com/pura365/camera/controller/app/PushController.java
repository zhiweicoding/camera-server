package com.pura365.camera.controller.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.pura365.camera.domain.UserPushToken;
import com.pura365.camera.enums.EnableStatus;
import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.model.push.RegisterPushTokenRequest;
import com.pura365.camera.repository.UserPushTokenRepository;
import com.pura365.camera.service.MessageService;
import com.pura365.camera.util.PushProviderUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Date;
import java.util.List;

/**
 * 推送管理接口
 */
@Tag(name = "推送管理", description = "推送Token注册、注销接口")
@RestController
@RequestMapping("/api/app/push")
public class PushController {

    private static final Logger log = LoggerFactory.getLogger(PushController.class);

    private final UserPushTokenRepository userPushTokenRepository;
    private final MessageService messageService;
    private final String pushProvider;
    private final String iosPushProvider;
    private final boolean enableBothProviders;

    public PushController(UserPushTokenRepository userPushTokenRepository,
                          MessageService messageService,
                          @Value("${push.provider:jpush}") String pushProvider,
                          @Value("${push.ios-provider:}") String iosPushProvider,
                          @Value("${push.enable-both-providers:true}") boolean enableBothProviders) {
        this.userPushTokenRepository = userPushTokenRepository;
        this.messageService = messageService;
        this.pushProvider = pushProvider;
        this.iosPushProvider = iosPushProvider;
        this.enableBothProviders = enableBothProviders;
    }

    /**
     * 注册推送Token
     * 
     * 客户端在APP启动或用户登录后调用此接口，注册推送通道的 token 到服务端
     */
    @Operation(summary = "注册推送Token", description = "客户端注册或更新推送Token")
    @PostMapping("/register")
    public ApiResponse<Void> registerPushToken(
            @RequestAttribute("currentUserId") Long currentUserId,
            @RequestBody RegisterPushTokenRequest request) {
        if (!StringUtils.hasText(request.getDeviceType())) {
            log.warn("注册推送Token失败，device_type为空 - userId={}", currentUserId);
            return ApiResponse.error(400, "device_type 不能为空");
        }
        String normalizedRegistrationId = PushProviderUtil.normalizeRegistrationId(request.getRegistrationId());
        if (!StringUtils.hasText(normalizedRegistrationId)) {
            log.warn("注册推送Token失败，registration_id为空 - userId={}", currentUserId);
            return ApiResponse.error(400, "registration_id 不能为空");
        }
        String normalizedProvider = PushProviderUtil.resolvePreferredProvider(
                request.getDeviceType(), request.getProvider(), request.getChannel(),
                pushProvider, iosPushProvider, enableBothProviders);
        String normalizedChannel = PushProviderUtil.resolveChannel(request.getChannel(), normalizedProvider);
        String canonicalDeviceType = PushProviderUtil.canonicalDeviceType(request.getDeviceType());

        logProviderOverrideIfNeeded(currentUserId, request.getDeviceType(), request.getProvider(),
                request.getChannel(), normalizedProvider);

        log.info("注册推送Token - userId={}, deviceType={}, provider={}, channel={}, registrationId={}, deviceModel={}, osVersion={}, appVersion={}",
                currentUserId, canonicalDeviceType, normalizedProvider, normalizedChannel,
                PushProviderUtil.maskToken(normalizedRegistrationId), request.getDeviceModel(), request.getOsVersion(),
                request.getAppVersion());

        Date now = new Date();
        int affectedRows = updatePushTokenByUniqueKey(currentUserId, normalizedRegistrationId, normalizedProvider,
                canonicalDeviceType, normalizedChannel, request, now);
        if (affectedRows > 0) {
            log.info("更新推送Token成功 - userId={}, affectedRows={}, provider={}, registrationId={}",
                    currentUserId, affectedRows, normalizedProvider,
                    PushProviderUtil.maskToken(normalizedRegistrationId));
            logTokenSnapshot(currentUserId, "update");
            return ApiResponse.success("注册成功", null);
        }

        try {
            UserPushToken token = buildPushToken(currentUserId, canonicalDeviceType, normalizedRegistrationId,
                    normalizedProvider, normalizedChannel, request, now);
            userPushTokenRepository.insert(token);
            log.info("新增推送Token成功 - userId={}, tokenId={}, provider={}, registrationId={}",
                    currentUserId, token.getId(), normalizedProvider,
                    PushProviderUtil.maskToken(normalizedRegistrationId));
        } catch (RuntimeException e) {
            if (!isDuplicatePushTokenException(e)) {
                throw e;
            }

            log.warn("注册推送Token遇到并发重复键，回退为更新 - userId={}, provider={}, registrationId={}",
                    currentUserId, normalizedProvider, PushProviderUtil.maskToken(normalizedRegistrationId));
            int fallbackRows = updatePushTokenByUniqueKey(currentUserId, normalizedRegistrationId, normalizedProvider,
                    canonicalDeviceType, normalizedChannel, request, now);
            if (fallbackRows <= 0) {
                throw e;
            }
            log.info("并发更新推送Token成功 - userId={}, affectedRows={}, provider={}, registrationId={}",
                    currentUserId, fallbackRows, normalizedProvider,
                    PushProviderUtil.maskToken(normalizedRegistrationId));
        }

        logTokenSnapshot(currentUserId, "insert");

        return ApiResponse.success("注册成功", null);
    }

    /**
     * 注销推送Token
     * 
     * 用户退出登录或删除APP时，客户端调用此接口注销推送Token
     */
    @Operation(summary = "注销推送Token", description = "客户端注销推送Token")
    @DeleteMapping("/unregister")
    public ApiResponse<Void> unregisterPushToken(
            @RequestAttribute("currentUserId") Long currentUserId,
            @RequestParam("registration_id") String registrationId,
            @RequestParam(value = "provider", required = false) String provider,
            @RequestParam(value = "channel", required = false) String channel,
            @RequestParam(value = "device_type", required = false) String deviceType) {
        String normalizedRegistrationId = PushProviderUtil.normalizeRegistrationId(registrationId);
        String normalizedProvider = null;
        if (StringUtils.hasText(provider) || StringUtils.hasText(channel) || StringUtils.hasText(deviceType)) {
            normalizedProvider = PushProviderUtil.resolvePreferredProvider(
                    deviceType, provider, channel, pushProvider, iosPushProvider, enableBothProviders);
        }

        log.info("注销推送Token - userId={}, provider={}, registrationId={}",
                currentUserId, normalizedProvider, PushProviderUtil.maskToken(normalizedRegistrationId));

        if (!StringUtils.hasText(normalizedRegistrationId)) {
            log.warn("注销推送Token失败，registration_id为空 - userId={}", currentUserId);
            return ApiResponse.error(400, "registration_id 不能为空");
        }

        LambdaUpdateWrapper<UserPushToken> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserPushToken::getUserId, currentUserId)
               .eq(UserPushToken::getRegistrationId, normalizedRegistrationId)
               .set(UserPushToken::getEnabled, EnableStatus.DISABLED)
               .set(UserPushToken::getUpdatedAt, new Date());
        if (StringUtils.hasText(normalizedProvider)) {
            wrapper.eq(UserPushToken::getProvider, normalizedProvider);
        }

        int affectedRows = userPushTokenRepository.update(null, wrapper);
        log.info("注销推送Token成功 - userId={}, affectedRows={}, provider={}, registrationId={}",
                currentUserId, affectedRows, normalizedProvider, PushProviderUtil.maskToken(normalizedRegistrationId));

        return ApiResponse.success("注销成功", null);
    }

    private void logProviderOverrideIfNeeded(Long userId,
                                             String deviceType,
                                             String requestProvider,
                                             String requestChannel,
                                             String resolvedProvider) {
        if (enableBothProviders) {
            return;
        }

        String rawProvider = PushProviderUtil.normalizeProvider(requestProvider);
        String rawChannel = PushProviderUtil.normalizeProvider(requestChannel);
        String requestedProvider = rawProvider != null ? rawProvider : rawChannel;
        if (requestedProvider == null || requestedProvider.equals(resolvedProvider)) {
            return;
        }

        log.info("推送provider已按配置强制覆盖 - userId={}, deviceType={}, requestedProvider={}, resolvedProvider={}, configuredProvider={}, configuredIosProvider={}",
                userId, PushProviderUtil.canonicalDeviceType(deviceType), requestedProvider, resolvedProvider,
                pushProvider, iosPushProvider);
    }

    private int updatePushTokenByUniqueKey(Long userId,
                                           String registrationId,
                                           String provider,
                                           String deviceType,
                                           String channel,
                                           RegisterPushTokenRequest request,
                                           Date now) {
        LambdaUpdateWrapper<UserPushToken> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UserPushToken::getUserId, userId)
                .eq(UserPushToken::getRegistrationId, registrationId)
                .eq(UserPushToken::getProvider, provider)
                .set(UserPushToken::getDeviceType, deviceType)
                .set(UserPushToken::getChannel, channel)
                .set(UserPushToken::getAppVersion, request.getAppVersion())
                .set(UserPushToken::getDeviceModel, request.getDeviceModel())
                .set(UserPushToken::getOsVersion, request.getOsVersion())
                .set(UserPushToken::getEnabled, EnableStatus.ENABLED)
                .set(UserPushToken::getUpdatedAt, now);
        return userPushTokenRepository.update(null, updateWrapper);
    }

    private UserPushToken buildPushToken(Long userId,
                                         String deviceType,
                                         String registrationId,
                                         String provider,
                                         String channel,
                                         RegisterPushTokenRequest request,
                                         Date now) {
        UserPushToken token = new UserPushToken();
        token.setUserId(userId);
        token.setDeviceType(deviceType);
        token.setRegistrationId(registrationId);
        token.setProvider(provider);
        token.setChannel(channel);
        token.setAppVersion(request.getAppVersion());
        token.setDeviceModel(request.getDeviceModel());
        token.setOsVersion(request.getOsVersion());
        token.setEnabled(EnableStatus.ENABLED);
        token.setCreatedAt(now);
        token.setUpdatedAt(now);
        return token;
    }

    private boolean isDuplicatePushTokenException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLIntegrityConstraintViolationException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.contains("uk_user_registration_provider")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void logTokenSnapshot(Long userId, String stage) {
        LambdaQueryWrapper<UserPushToken> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPushToken::getUserId, userId);
        List<UserPushToken> tokens = userPushTokenRepository.selectList(wrapper);
        String snapshot = tokens.stream()
                .map(token -> "id=" + token.getId()
                        + ",provider=" + token.getProvider()
                        + ",channel=" + token.getChannel()
                        + ",deviceType=" + token.getDeviceType()
                        + ",enabled=" + token.getEnabled()
                        + ",appVersion=" + token.getAppVersion()
                        + ",deviceModel=" + token.getDeviceModel()
                        + ",osVersion=" + token.getOsVersion()
                        + ",registrationId=" + PushProviderUtil.maskToken(token.getRegistrationId()))
                .reduce((left, right) -> left + "; " + right)
                .orElse("EMPTY");
        log.info("注册推送Token后快照 - userId={}, stage={}, tokens=[{}]", userId, stage, snapshot);
    }

    /**
     * 测试推送
     * 
     * 手动触发一条推送消息到当前用户，用于测试推送功能是否正常
     */
    @Operation(summary = "测试推送", description = "手动触发一条测试推送消息到当前用户")
    @PostMapping("/test")
    public ApiResponse<Long> testPush(
            @RequestAttribute("currentUserId") Long currentUserId,
            @RequestParam(value = "device_id", required = false) String deviceId,
            @RequestParam(value = "title", required = false, defaultValue = "测试推送") String title,
            @RequestParam(value = "content", required = false, defaultValue = "这是一条测试消息") String content) {
        log.info("测试推送 - userId={}, deviceId={}, title={}, content={}",
                currentUserId, deviceId, title, content);

        try {
            Long messageId = messageService.createMessageAndPush(
                    currentUserId,
                    deviceId,
                    "test",
                    title,
                    content,
                    null,
                    null);
            log.info("测试推送成功 - userId={}, messageId={}", currentUserId, messageId);
            return ApiResponse.success("推送成功", messageId);
        } catch (Exception e) {
            log.error("测试推送失败 - userId={}", currentUserId, e);
            return ApiResponse.error(500, "推送失败: " + e.getMessage());
        }
    }
}
