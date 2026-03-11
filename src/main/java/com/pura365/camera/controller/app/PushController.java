package com.pura365.camera.controller.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.pura365.camera.domain.UserPushToken;
import com.pura365.camera.enums.EnableStatus;
import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.model.push.RegisterPushTokenRequest;
import com.pura365.camera.repository.UserPushTokenRepository;
import com.pura365.camera.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

@Tag(name = "Push", description = "Push token register/unregister APIs")
@RestController
@RequestMapping("/api/app/push")
public class PushController {

    @Value("${push.provider:jpush}")
    private String pushProvider;

    private static final Logger log = LoggerFactory.getLogger(PushController.class);

    private final UserPushTokenRepository userPushTokenRepository;
    private final MessageService messageService;

    public PushController(UserPushTokenRepository userPushTokenRepository,
                          MessageService messageService) {
        this.userPushTokenRepository = userPushTokenRepository;
        this.messageService = messageService;
    }

    @Operation(summary = "Register push token", description = "Register or refresh push token from app")
    @PostMapping("/register")
    public ApiResponse<Void> registerPushToken(
            @RequestAttribute("currentUserId") Long currentUserId,
            @RequestBody RegisterPushTokenRequest request) {
        String normalizedDeviceType = normalizeText(request.getDeviceType());
        String normalizedRegistrationId = normalizeText(request.getRegistrationId());
        String normalizedAppVersion = normalizeText(request.getAppVersion());
        String normalizedDeviceModel = normalizeText(request.getDeviceModel());
        String normalizedOsVersion = normalizeText(request.getOsVersion());

        log.info("Push token source=app_register_api userId={} registrationId={} rawRegistrationIdLength={} deviceType={} appVersion={} deviceModel={} osVersion={}",
                currentUserId,
                maskRegistrationId(normalizedRegistrationId),
                request.getRegistrationId() == null ? 0 : request.getRegistrationId().length(),
                normalizedDeviceType,
                normalizedAppVersion,
                normalizedDeviceModel,
                normalizedOsVersion);

        if (!StringUtils.hasText(normalizedDeviceType)) {
            log.warn("Push token register failed: empty device_type userId={}", currentUserId);
            return ApiResponse.error(400, "device_type 不能为空");
        }
        if (!StringUtils.hasText(normalizedRegistrationId)) {
            log.warn("Push token register failed: empty registration_id userId={}", currentUserId);
            return ApiResponse.error(400, "registration_id 不能为空");
        }

        LambdaQueryWrapper<UserPushToken> regWrapper = new LambdaQueryWrapper<>();
        regWrapper.eq(UserPushToken::getUserId, currentUserId)
                .eq(UserPushToken::getProvider, pushProvider);
        UserPushToken existingToken = userPushTokenRepository.selectOne(regWrapper);

        if (existingToken != null) {
            existingToken.setDeviceType(normalizedDeviceType);
            existingToken.setAppVersion(normalizedAppVersion);
            existingToken.setDeviceModel(normalizedDeviceModel);
            existingToken.setOsVersion(normalizedOsVersion);
            existingToken.setProvider(pushProvider);
            existingToken.setChannel(pushProvider);
            existingToken.setUpdatedAt(new Date());
            userPushTokenRepository.updateById(existingToken);

            log.info("Push token source=user_push_token_table action=update userId={} tokenId={} registrationId={} enabled={} enabledTokenCount={}",
                    currentUserId,
                    existingToken.getId(),
                    maskRegistrationId(existingToken.getRegistrationId()),
                    existingToken.getEnabled(),
                    countEnabledTokens(currentUserId));
        } else {
            UserPushToken token = new UserPushToken();
            token.setUserId(currentUserId);
            token.setDeviceType(normalizedDeviceType);
            token.setRegistrationId(normalizedRegistrationId);
            token.setAppVersion(normalizedAppVersion);
            token.setDeviceModel(normalizedDeviceModel);
            token.setOsVersion(normalizedOsVersion);
            token.setEnabled(EnableStatus.ENABLED);
            token.setProvider(pushProvider);
            token.setChannel(pushProvider);
            token.setCreatedAt(new Date());
            token.setUpdatedAt(new Date());
            userPushTokenRepository.insert(token);

            log.info("Push token source=user_push_token_table action=insert userId={} tokenId={} registrationId={} enabled={} enabledTokenCount={}",
                    currentUserId,
                    token.getId(),
                    maskRegistrationId(token.getRegistrationId()),
                    token.getEnabled(),
                    countEnabledTokens(currentUserId));
        }

        return ApiResponse.success("注册成功", null);
    }

    @Operation(summary = "Unregister push token", description = "Disable push token from app")
    @DeleteMapping("/unregister")
    public ApiResponse<Void> unregisterPushToken(
            @RequestAttribute("currentUserId") Long currentUserId,
            @RequestParam("registration_id") String registrationId) {
        String normalizedRegistrationId = normalizeText(registrationId);

        log.info("Push token source=app_unregister_api userId={} registrationId={} rawRegistrationIdLength={}",
                currentUserId,
                maskRegistrationId(normalizedRegistrationId),
                registrationId == null ? 0 : registrationId.length());

        if (!StringUtils.hasText(normalizedRegistrationId)) {
            log.warn("Push token unregister failed: empty registration_id userId={}", currentUserId);
            return ApiResponse.error(400, "registration_id 不能为空");
        }

        LambdaUpdateWrapper<UserPushToken> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserPushToken::getUserId, currentUserId)
                .eq(UserPushToken::getRegistrationId, normalizedRegistrationId)
                .set(UserPushToken::getEnabled, EnableStatus.DISABLED)
                .set(UserPushToken::getUpdatedAt, new Date());

        int affectedRows = userPushTokenRepository.update(null, wrapper);
        log.info("Push token source=user_push_token_table action=disable userId={} registrationId={} affectedRows={} enabledTokenCount={}",
                currentUserId,
                maskRegistrationId(normalizedRegistrationId),
                affectedRows,
                countEnabledTokens(currentUserId));

        return ApiResponse.success("注销成功", null);
    }

    @Operation(summary = "Test push", description = "Trigger one test push message to current user")
    @PostMapping("/test")
    public ApiResponse<Long> testPush(
            @RequestAttribute("currentUserId") Long currentUserId,
            @RequestParam(value = "device_id", required = false) String deviceId,
            @RequestParam(value = "title", required = false, defaultValue = "测试推送") String title,
            @RequestParam(value = "content", required = false, defaultValue = "这是一条测试消息") String content) {
        log.info("Test push trigger userId={} deviceId={} title={} content={}",
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
            log.info("Test push success userId={} messageId={}", currentUserId, messageId);
            return ApiResponse.success("推送成功", messageId);
        } catch (Exception e) {
            log.error("Test push failed userId={}", currentUserId, e);
            return ApiResponse.error(500, "推送失败: " + e.getMessage());
        }
    }

    private long countEnabledTokens(Long userId) {
        LambdaQueryWrapper<UserPushToken> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPushToken::getUserId, userId)
                .eq(UserPushToken::getEnabled, EnableStatus.ENABLED);
        Long count = userPushTokenRepository.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
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
}
