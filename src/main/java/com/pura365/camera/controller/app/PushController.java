package com.pura365.camera.controller.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.pura365.camera.domain.UserPushToken;
import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.model.push.RegisterPushTokenRequest;
import com.pura365.camera.repository.UserPushTokenRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

/**
 * 推送管理接口
 */
@Tag(name = "推送管理", description = "推送Token注册、注销接口")
@RestController
@RequestMapping("/api/app/push")
public class PushController {

    private static final Logger log = LoggerFactory.getLogger(PushController.class);

    private final UserPushTokenRepository userPushTokenRepository;

    public PushController(UserPushTokenRepository userPushTokenRepository) {
        this.userPushTokenRepository = userPushTokenRepository;
    }

    /**
     * 注册推送Token
     * 
     * 客户端在APP启动或用户登录后调用此接口，将极光推送的Registration ID注册到服务端
     */
    @Operation(summary = "注册推送Token", description = "客户端注册或更新推送Token")
    @PostMapping("/register")
    public ApiResponse<Void> registerPushToken(
            @RequestAttribute("currentUserId") Long currentUserId,
            @RequestBody RegisterPushTokenRequest request) {
        log.info("注册推送Token - userId={}, deviceType={}, registrationId={}, deviceModel={}, osVersion={}, appVersion={}",
                currentUserId, request.getDeviceType(), request.getRegistrationId(),
                request.getDeviceModel(), request.getOsVersion(), request.getAppVersion());

        if (!StringUtils.hasText(request.getDeviceType())) {
            log.warn("注册推送Token失败，device_type为空 - userId={}", currentUserId);
            return ApiResponse.error(400, "device_type 不能为空");
        }
        if (!StringUtils.hasText(request.getRegistrationId())) {
            log.warn("注册推送Token失败，registration_id为空 - userId={}", currentUserId);
            return ApiResponse.error(400, "registration_id 不能为空");
        }

        // 检查是否已存在
        LambdaQueryWrapper<UserPushToken> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPushToken::getUserId, currentUserId)
               .eq(UserPushToken::getRegistrationId, request.getRegistrationId());
        UserPushToken existingToken = userPushTokenRepository.selectOne(wrapper);

        if (existingToken != null) {
            // 更新现有记录
            existingToken.setDeviceType(request.getDeviceType());
            existingToken.setAppVersion(request.getAppVersion());
            existingToken.setDeviceModel(request.getDeviceModel());
            existingToken.setOsVersion(request.getOsVersion());
            existingToken.setEnabled(1);
            existingToken.setUpdatedAt(new Date());
            userPushTokenRepository.updateById(existingToken);
            log.info("更新推送Token成功 - userId={}, tokenId={}", currentUserId, existingToken.getId());
        } else {
            // 创建新记录
            UserPushToken token = new UserPushToken();
            token.setUserId(currentUserId);
            token.setDeviceType(request.getDeviceType());
            token.setRegistrationId(request.getRegistrationId());
            token.setAppVersion(request.getAppVersion());
            token.setDeviceModel(request.getDeviceModel());
            token.setOsVersion(request.getOsVersion());
            token.setEnabled(1);
            token.setCreatedAt(new Date());
            token.setUpdatedAt(new Date());
            userPushTokenRepository.insert(token);
            log.info("新增推送Token成功 - userId={}, tokenId={}", currentUserId, token.getId());
        }

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
            @RequestParam("registration_id") String registrationId) {
        log.info("注销推送Token - userId={}, registrationId={}", currentUserId, registrationId);

        if (!StringUtils.hasText(registrationId)) {
            log.warn("注销推送Token失败，registration_id为空 - userId={}", currentUserId);
            return ApiResponse.error(400, "registration_id 不能为空");
        }

        // 禁用或删除token
        LambdaUpdateWrapper<UserPushToken> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserPushToken::getUserId, currentUserId)
               .eq(UserPushToken::getRegistrationId, registrationId)
               .set(UserPushToken::getEnabled, 0);
        
        userPushTokenRepository.update(null, wrapper);
        log.info("注销推送Token成功 - userId={}, registrationId={}", currentUserId, registrationId);

        return ApiResponse.success("注销成功", null);
    }
}
