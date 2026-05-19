package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pura365.camera.config.JPushConfig;
import com.pura365.camera.domain.UserPushToken;
import com.pura365.camera.repository.UserPushTokenRepository;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JPushServiceRoutePolicyTest {

    @Test
    void pushToUser_keepsApnsTokensWhenOverseaOverrideFiltersJPush() {
        TestFixture fixture = newFixture(false, "us");

        boolean result = fixture.service.pushToUser(
                7L,
                "报警通知",
                "检测到人员移动",
                Collections.singletonMap("type", "motion"));

        assertTrue(result);
        verify(fixture.apnsPushService).pushToTokens(
                eq(Collections.singletonList("abcdef123456")),
                eq("报警通知"),
                eq("检测到人员移动"),
                anyMap());
        verify(fixture.firebasePushService, never())
                .pushToTokens(anyList(), anyString(), anyString(), anyMap());
    }

    @Test
    void pushToUser_keepsApnsTokensWhenChinaOverrideFiltersFirebase() {
        TestFixture fixture = newFixture(true, "cn");

        boolean result = fixture.service.pushToUser(
                7L,
                "报警通知",
                "检测到人员移动",
                Collections.singletonMap("type", "motion"));

        assertTrue(result);
        verify(fixture.apnsPushService).pushToTokens(
                eq(Collections.singletonList("abcdef123456")),
                eq("报警通知"),
                eq("检测到人员移动"),
                anyMap());
        verify(fixture.firebasePushService, never())
                .pushToTokens(anyList(), anyString(), anyString(), anyMap());
    }

    private TestFixture newFixture(boolean chinaOverride, String region) {
        UserPushTokenRepository userPushTokenRepository = mock(UserPushTokenRepository.class);
        FirebasePushService firebasePushService = mock(FirebasePushService.class);
        ApnsPushService apnsPushService = mock(ApnsPushService.class);
        RegionRoutingService regionRoutingService = mock(RegionRoutingService.class);
        JPushConfig jPushConfig = mock(JPushConfig.class);

        UserPushToken apnsToken = new UserPushToken();
        apnsToken.setId(1L);
        apnsToken.setUserId(7L);
        apnsToken.setDeviceType("iOS");
        apnsToken.setProvider("apns");
        apnsToken.setChannel("apns");
        apnsToken.setRegistrationId("abcdef123456");

        when(userPushTokenRepository.selectList(anyTokenQuery()))
                .thenReturn(Collections.singletonList(apnsToken));
        when(regionRoutingService.hasExplicitOverride()).thenReturn(true);
        when(regionRoutingService.resolveRegion()).thenReturn(region);
        when(regionRoutingService.isChina()).thenReturn(chinaOverride);
        when(apnsPushService.pushToTokens(anyList(), anyString(), anyString(), anyMap()))
                .thenReturn(true);

        JPushService service = new JPushService(
                null,
                jPushConfig,
                userPushTokenRepository,
                firebasePushService,
                apnsPushService,
                regionRoutingService,
                "jpush",
                "");

        return new TestFixture(service, firebasePushService, apnsPushService);
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<UserPushToken> anyTokenQuery() {
        return any(LambdaQueryWrapper.class);
    }

    private static class TestFixture {
        private final JPushService service;
        private final FirebasePushService firebasePushService;
        private final ApnsPushService apnsPushService;

        private TestFixture(JPushService service,
                            FirebasePushService firebasePushService,
                            ApnsPushService apnsPushService) {
            this.service = service;
            this.firebasePushService = firebasePushService;
            this.apnsPushService = apnsPushService;
        }
    }
}
