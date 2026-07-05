package com.pura365.camera.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PushProviderUtilTest {

    @Test
    void resolvePreferredProvider_defaultsIosToApnsWhenIosProviderIsBlank() {
        assertEquals(
                "apns",
                PushProviderUtil.resolvePreferredProvider(
                        "iOS", null, "apns", "jpush", "", false));
    }

    @Test
    void resolveConfiguredProvider_defaultsIosToApnsInsteadOfGlobalProvider() {
        assertEquals(
                "apns",
                PushProviderUtil.resolveConfiguredProvider("iOS", "jpush", null));
    }

    @Test
    void resolveConfiguredProvider_honorsExplicitIosOverride() {
        assertEquals(
                "jpush",
                PushProviderUtil.resolveConfiguredProvider("iOS", "apns", "jpush"));
    }

    @Test
    void resolveConfiguredProvider_keepsGlobalProviderForAndroid() {
        assertEquals(
                "jpush",
                PushProviderUtil.resolveConfiguredProvider("Android", "jpush", null));
    }
}
