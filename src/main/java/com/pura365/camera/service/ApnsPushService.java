package com.pura365.camera.service;

import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.ApnsClientBuilder;
import com.eatthepath.pushy.apns.PushNotificationResponse;
import com.eatthepath.pushy.apns.auth.ApnsSigningKey;
import com.eatthepath.pushy.apns.util.SimpleApnsPayloadBuilder;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * APNs direct push sender for iOS devices.
 */
@Service
public class ApnsPushService implements DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(ApnsPushService.class);

    private final ResourceLoader resourceLoader;

    @Value("${apns.auth-key-path:}")
    private String authKeyPath;

    @Value("${apns.key-id:}")
    private String keyId;

    @Value("${apns.team-id:}")
    private String teamId;

    @Value("${apns.topic:}")
    private String topic;

    @Value("${apns.use-sandbox:true}")
    private boolean useSandbox;

    private final Object clientLock = new Object();
    private volatile ApnsClient apnsClient;

    public ApnsPushService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public boolean pushToTokens(List<String> deviceTokens,
                                String title,
                                String content,
                                Map<String, String> extras) {
        if (deviceTokens == null || deviceTokens.isEmpty()) {
            return false;
        }
        if (!isConfigured()) {
            logger.warn("APNs push skipped: auth key / key id / team id / topic is not configured");
            return false;
        }

        List<String> normalizedTokens = deviceTokens.stream()
                .filter(StringUtils::hasText)
                .map(this::normalizeDeviceToken)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (normalizedTokens.isEmpty()) {
            return false;
        }

        boolean anySuccess = false;
        for (String token : normalizedTokens) {
            if (pushSingle(token, title, content, extras)) {
                anySuccess = true;
            }
        }
        return anySuccess;
    }

    private boolean pushSingle(String deviceToken,
                               String title,
                               String content,
                               Map<String, String> extras) {
        try {
            ApnsClient client = getClient();
            if (client == null) {
                return false;
            }

            String payload = buildPayload(title, content, extras);
            SimpleApnsPushNotification notification =
                    new SimpleApnsPushNotification(deviceToken, topic, payload);

            PushNotificationResponse<SimpleApnsPushNotification> response =
                    client.sendNotification(notification).get(15, TimeUnit.SECONDS);

            if (response.isAccepted()) {
                logger.info("APNs success token={}, topic={}", maskToken(deviceToken), topic);
                return true;
            }

            logger.warn("APNs rejected token={}, reason={}, invalidationTimestamp={}",
                    maskToken(deviceToken), response.getRejectionReason(),
                    response.getTokenInvalidationTimestamp());
            return false;
        } catch (Exception e) {
            logger.error("APNs push exception token={}", maskToken(deviceToken), e);
            return false;
        }
    }

    private String buildPayload(String title,
                                String content,
                                Map<String, String> extras) {
        SimpleApnsPayloadBuilder builder = new SimpleApnsPayloadBuilder();

        if (StringUtils.hasText(title)) {
            builder.setAlertTitle(title);
        }
        if (StringUtils.hasText(content)) {
            builder.setAlertBody(content);
        }
        builder.setSoundFileName("default");

        Map<String, Object> customProperties = new LinkedHashMap<>();
        if (extras != null) {
            extras.forEach((key, value) -> {
                if (StringUtils.hasText(key) && value != null) {
                    customProperties.put(key, value);
                }
            });
        }

        for (Map.Entry<String, Object> entry : customProperties.entrySet()) {
            builder.addCustomProperty(entry.getKey(), entry.getValue());
        }

        return builder.build();
    }

    private ApnsClient getClient() {
        if (apnsClient != null) {
            return apnsClient;
        }

        synchronized (clientLock) {
            if (apnsClient != null) {
                return apnsClient;
            }

            try {
                Resource resource = resourceLoader.getResource(authKeyPath);
                if (!resource.exists()) {
                    logger.error("APNs auth key does not exist: {}", authKeyPath);
                    return null;
                }

                File keyFile = resource.getFile();
                ApnsSigningKey signingKey = ApnsSigningKey.loadFromPkcs8File(keyFile, teamId, keyId);

                String host = useSandbox
                        ? ApnsClientBuilder.DEVELOPMENT_APNS_HOST
                        : ApnsClientBuilder.PRODUCTION_APNS_HOST;

                apnsClient = new ApnsClientBuilder()
                        .setApnsServer(host)
                        .setSigningKey(signingKey)
                        .build();
                logger.info("APNs client initialized: host={}, topic={}", host, topic);
                return apnsClient;
            } catch (Exception e) {
                logger.error("Failed to initialize APNs client", e);
                return null;
            }
        }
    }

    private boolean isConfigured() {
        return StringUtils.hasText(authKeyPath)
                && StringUtils.hasText(keyId)
                && StringUtils.hasText(teamId)
                && StringUtils.hasText(topic);
    }

    private String normalizeDeviceToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        return token.trim()
                .replace(" ", "")
                .replace("<", "")
                .replace(">", "");
    }

    private String maskToken(String token) {
        if (!StringUtils.hasText(token)) {
            return "EMPTY";
        }
        String value = token.trim();
        if (value.length() <= 12) {
            return value;
        }
        return value.substring(0, 6) + "..." + value.substring(value.length() - 6);
    }

    @Override
    public void destroy() throws Exception {
        if (apnsClient != null) {
            apnsClient.close().get(5, TimeUnit.SECONDS);
        }
    }
}
