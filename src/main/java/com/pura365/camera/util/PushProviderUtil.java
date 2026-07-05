package com.pura365.camera.util;

import org.springframework.util.StringUtils;

/**
 * 推送 provider 解析工具，统一处理 jpush/fcm/apns 的兼容别名与默认值。
 */
public final class PushProviderUtil {

    private PushProviderUtil() {
    }

    public static String normalizeProvider(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = raw.trim().toLowerCase();
        if ("firebase".equals(normalized)) {
            return "fcm";
        }
        if ("apple".equals(normalized) || "ios".equals(normalized)) {
            return "apns";
        }
        if ("fcm".equals(normalized) || "jpush".equals(normalized) || "apns".equals(normalized)) {
            return normalized;
        }
        return null;
    }

    public static String resolvePreferredProvider(String deviceType,
                                                  String provider,
                                                  String channel,
                                                  String configuredProvider,
                                                  String configuredIosProvider) {
        return resolvePreferredProvider(deviceType, provider, channel, configuredProvider, configuredIosProvider, true);
    }

    public static String resolvePreferredProvider(String deviceType,
                                                  String provider,
                                                  String channel,
                                                  String configuredProvider,
                                                  String configuredIosProvider,
                                                  boolean allowClientProvider) {
        if (allowClientProvider) {
            String resolved = normalizeProvider(provider);
            if (resolved != null) {
                return resolved;
            }

            resolved = normalizeProvider(channel);
            if (resolved != null) {
                return resolved;
            }
        }

        return resolveConfiguredProvider(deviceType, configuredProvider, configuredIosProvider);
    }

    public static String resolveConfiguredProvider(String deviceType,
                                                   String configuredProvider,
                                                   String configuredIosProvider) {
        String resolved;
        if ("ios".equals(normalizeDeviceType(deviceType))) {
            resolved = normalizeProvider(configuredIosProvider);
            if (resolved != null) {
                return resolved;
            }
            return "apns";
        }

        resolved = normalizeProvider(configuredProvider);
        if (resolved != null) {
            return resolved;
        }

        return "jpush";
    }

    public static String resolveChannel(String channel, String provider) {
        String normalizedChannel = normalizeProvider(channel);
        return normalizedChannel != null ? normalizedChannel : provider;
    }

    public static String normalizeRegistrationId(String registrationId) {
        return StringUtils.hasText(registrationId) ? registrationId.trim() : null;
    }

    public static String normalizeDeviceType(String deviceType) {
        return StringUtils.hasText(deviceType) ? deviceType.trim().toLowerCase() : null;
    }

    public static String canonicalDeviceType(String deviceType) {
        String normalized = normalizeDeviceType(deviceType);
        if ("ios".equals(normalized)) {
            return "iOS";
        }
        if ("android".equals(normalized)) {
            return "Android";
        }
        return StringUtils.hasText(deviceType) ? deviceType.trim() : deviceType;
    }

    public static String maskToken(String token) {
        if (!StringUtils.hasText(token)) {
            return "EMPTY";
        }
        String value = token.trim();
        if (value.length() <= 8) {
            return value;
        }
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }
}
