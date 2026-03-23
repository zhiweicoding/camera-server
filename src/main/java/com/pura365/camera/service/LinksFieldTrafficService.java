package com.pura365.camera.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * LinksField 4G traffic query service.
 */
@Service
public class LinksFieldTrafficService {

    private static final Logger log = LoggerFactory.getLogger(LinksFieldTrafficService.class);

    @Value("${linksfield.api-base-url:https://api.linksfield.net}")
    private String apiBaseUrl;

    @Value("${linksfield.access-key-id:}")
    private String accessKeyId;

    @Value("${linksfield.private-key:}")
    private String privateKey;

    @Value("${linksfield.api-version:2.0}")
    private String apiVersion;

    @Value("${linksfield.signature-type:2.0}")
    private String signatureType;

    @Value("${linksfield.accept-language:zh-CN}")
    private String acceptLanguage;

    @Value("${linksfield.request-timeout-seconds:10}")
    private int requestTimeoutSeconds;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Query SIM remaining data.
     * Try "/remaining_data" first, fallback to "/bundles" for diagnostics.
     */
    public Map<String, Object> queryRemainingData(String simId) {
        if (!StringUtils.hasText(simId)) {
            throw new RuntimeException("sim_id 不能为空");
        }
        validateConfig();

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(requestTimeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(requestTimeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(requestTimeoutSeconds, TimeUnit.SECONDS)
                .build();

        RuntimeException remainingError = null;
        try {
            return queryRemainingDataByPath(client, simId, "remaining_data");
        } catch (RuntimeException e) {
            remainingError = e;
            log.warn("LinksField remaining_data query failed, fallback to bundles, simId={}", simId, e);
        }

        try {
            return queryRemainingDataByPath(client, simId, "bundles");
        } catch (RuntimeException e) {
            log.error("LinksField bundles query failed, simId={}", simId, e);
            String remainingMsg = remainingError == null ? "unknown" : remainingError.getMessage();
            throw new RuntimeException("查询4G流量失败: remaining_data=" + remainingMsg + "; bundles=" + e.getMessage());
        }
    }

    private Map<String, Object> queryRemainingDataByPath(OkHttpClient client, String simId, String pathSegment) {
        long timestamp = System.currentTimeMillis();
        int nonce = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        boolean includeTimestampAsQuery = "remaining_data".equals(pathSegment);
        Map<String, Object> queryParams = includeTimestampAsQuery
                ? buildTimestampNonceQueryParams(timestamp, nonce)
                : Collections.emptyMap();
        String signUriBase = String.format("/cube/v4/sims/%s/%s", simId, pathSegment);
        String signUri = signUriBase;

        Map<String, Object> signData = buildSignData(signUri, queryParams, null, timestamp, nonce);
        String signPayloadJson = toJson(signData);
        String sign = signPayload(signPayloadJson);
        String authorization = "LF " + accessKeyId + "/" + sign;

        HttpUrl baseUrl = HttpUrl.parse(apiBaseUrl);
        if (baseUrl == null) {
            throw new RuntimeException("linksfield.api-base-url 配置无效");
        }
        HttpUrl.Builder urlBuilder = baseUrl.newBuilder()
                .addPathSegments("cube/v4/sims")
                .addPathSegment(simId)
                .addPathSegment(pathSegment);
        for (Map.Entry<String, Object> entry : queryParams.entrySet()) {
            if (entry.getValue() != null) {
                urlBuilder.addQueryParameter(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        HttpUrl requestUrl = urlBuilder.build();

        Request request = new Request.Builder()
                .url(requestUrl)
                .get()
                .addHeader("Accept-Language", acceptLanguage)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", authorization)
                .addHeader("X-LF-Api-Version", apiVersion)
                .addHeader("X-LF-Signature-Type", signatureType)
                .addHeader("timestamp", String.valueOf(timestamp))
                .addHeader("nonce", String.valueOf(nonce))
                .addHeader("x-lf-timestamp", String.valueOf(timestamp))
                .addHeader("x-lf-nonce", String.valueOf(nonce))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseText = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new RuntimeException("LinksField 请求失败，HTTP " + response.code()
                        + ", body=" + abbreviate(responseText, 500));
            }
            if (!StringUtils.hasText(responseText)) {
                throw new RuntimeException("LinksField 返回空响应");
            }

            Map<String, Object> result = objectMapper.readValue(
                    responseText,
                    new TypeReference<Map<String, Object>>() {
                    }
            );
            validateThirdResponse(result);
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("LinksField 请求异常: " + e.getMessage(), e);
        }
    }

    private void validateConfig() {
        if (!StringUtils.hasText(accessKeyId)) {
            throw new RuntimeException("linksfield.access-key-id 未配置");
        }
        if (!StringUtils.hasText(privateKey)) {
            throw new RuntimeException("linksfield.private-key 未配置");
        }
    }

    /**
     * Build signature payload:
     * - query params
     * - body params
     * - timestamp / nonce / x-sign-uri
     * Sorted then JSON serialized.
     */
    private Map<String, Object> buildSignData(String signUri,
                                              Map<String, Object> queryParams,
                                              Map<String, Object> body,
                                              long timestamp,
                                              int nonce) {
        Map<String, Object> data = new TreeMap<String, Object>();
        if (queryParams != null) {
            for (Map.Entry<String, Object> entry : queryParams.entrySet()) {
                if (entry.getValue() != null) {
                    data.put(entry.getKey(), entry.getValue());
                }
            }
        }
        if (body != null) {
            for (Map.Entry<String, Object> entry : body.entrySet()) {
                if (entry.getValue() != null) {
                    data.put(entry.getKey(), entry.getValue());
                }
            }
        }
        data.put("nonce", String.valueOf(nonce));
        data.put("timestamp", String.valueOf(timestamp));
        data.put("x-sign-uri", signUri);
        return data;
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new RuntimeException("签名数据序列化失败: " + e.getMessage());
        }
    }

    private String signPayload(String payload) {
        try {
            String algorithm = resolveSignAlgorithm(signatureType);
            PrivateKey rsaPrivateKey = parsePrivateKey(privateKey);

            Signature signature = Signature.getInstance(algorithm);
            signature.initSign(rsaPrivateKey);
            signature.update(payload.getBytes(StandardCharsets.UTF_8));
            byte[] signed = signature.sign();
            return Base64.getEncoder().encodeToString(signed);
        } catch (Exception e) {
            throw new RuntimeException("LinksField 签名失败: " + e.getMessage());
        }
    }

    private String resolveSignAlgorithm(String signType) {
        // LinksField v2 doc states "sign=Sign(data, algo=sha1withRSA, key=privateKey)".
        // Existing v1 APIs are also compatible with SHA1withRSA in current integration.
        return "SHA1withRSA";
    }

    private PrivateKey parsePrivateKey(String input) throws Exception {
        String normalized = input.replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(normalized);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }

    @SuppressWarnings("unchecked")
    private void validateThirdResponse(Map<String, Object> response) {
        if (response == null) {
            throw new RuntimeException("LinksField 返回空响应");
        }

        Object codeObj = response.get("code");
        if (codeObj != null && !isSuccessCode(String.valueOf(codeObj))) {
            Object topMsg = response.containsKey("message") ? response.get("message") : response.get("msg");
            throw new RuntimeException("LinksField 返回失败: code=" + codeObj + ", msg=" + topMsg);
        }

        Object statusObj = response.get("status");
        if (statusObj instanceof Map) {
            Map<String, Object> statusMap = (Map<String, Object>) statusObj;
            Object thirdCode = statusMap.get("code");
            if (thirdCode != null && !isSuccessCode(String.valueOf(thirdCode))) {
                Object msg = statusMap.containsKey("message") ? statusMap.get("message") : statusMap.get("msg");
                throw new RuntimeException("LinksField 返回失败: code=" + thirdCode + ", msg=" + msg);
            }
        }
    }

    private boolean isSuccessCode(String code) {
        if (code == null) {
            return false;
        }
        String normalized = code.trim();
        return "0".equals(normalized)
                || "200".equals(normalized)
                || "success".equalsIgnoreCase(normalized)
                || "ok".equalsIgnoreCase(normalized)
                || "CB-00-0000".equalsIgnoreCase(normalized);
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\r", " ").replace("\n", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private Map<String, Object> buildTimestampNonceQueryParams(long timestamp, int nonce) {
        Map<String, Object> queryParams = new TreeMap<String, Object>();
        queryParams.put("timestamp", String.valueOf(timestamp));
        queryParams.put("nonce", String.valueOf(nonce));
        return queryParams;
    }

}
