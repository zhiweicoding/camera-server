package com.pura365.camera.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Apple支付服务(App内购买IAP)
 */
@Service
public class ApplePayService {

    private static final Logger logger = LoggerFactory.getLogger(ApplePayService.class);

    @Value("${apple.pay.sandbox-url}")
    private String sandboxUrl;

    @Value("${apple.pay.production-url}")
    private String productionUrl;

    @Value("${apple.pay.shared-secret}")
    private String sharedSecret;

    @Value("${apple.pay.use-sandbox:true}")
    private Boolean useSandbox;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 验证Apple收据
     *
     * @param receiptData Base64编码的收据数据
     * @return 验证结果
     */
    public AppleReceiptVerifyResult verifyReceipt(String receiptData) {
        AppleReceiptVerifyResult result = new AppleReceiptVerifyResult();

        try {
            // 首先使用配置的环境验证
            String url = useSandbox ? sandboxUrl : productionUrl;
            JsonNode response = sendVerifyRequest(url, receiptData);

            int status = response.get("status").asInt();

            // 如果是生产环境返回21007(沙盒收据),自动切换到沙盒环境重试
            if (!useSandbox && status == 21007) {
                logger.info("生产环境返回沙盒收据,切换到沙盒环境验证");
                response = sendVerifyRequest(sandboxUrl, receiptData);
                status = response.get("status").asInt();
            }

            result.setStatus(status);
            result.setSuccess(status == 0);

            if (status == 0) {
                // 解析收据信息
                JsonNode receipt = response.get("receipt");
                if (receipt != null) {
                    result.setBundleId(receipt.get("bundle_id").asText());
                    result.setApplicationVersion(receipt.get("application_version").asText());
                }

                // 解析内购信息
                JsonNode inApp = receipt != null ? receipt.get("in_app") : null;
                if (inApp != null && inApp.isArray() && inApp.size() > 0) {
                    JsonNode purchase = inApp.get(0);
                    result.setProductId(purchase.get("product_id").asText());
                    result.setTransactionId(purchase.get("transaction_id").asText());
                    result.setOriginalTransactionId(purchase.get("original_transaction_id").asText());
                    result.setPurchaseDate(purchase.get("purchase_date_ms").asText());
                }

                logger.info("Apple收据验证成功: productId={}, transactionId={}", 
                        result.getProductId(), result.getTransactionId());
            } else {
                result.setErrorMessage(getStatusMessage(status));
                logger.warn("Apple收据验证失败: status={}, message={}", status, result.getErrorMessage());
            }

        } catch (Exception e) {
            logger.error("Apple收据验证异常", e);
            result.setSuccess(false);
            result.setErrorMessage("验证失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 发送验证请求到Apple服务器
     */
    private JsonNode sendVerifyRequest(String url, String receiptData) throws IOException {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("receipt-data", receiptData);
        requestBody.put("password", sharedSecret);
        requestBody.put("exclude-old-transactions", true);

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(jsonBody, MediaType.get("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("请求失败: " + response);
            }

            String responseBody = response.body().string();
            return objectMapper.readTree(responseBody);
        }
    }

    /**
     * 获取状态码对应的错误消息
     */
    private String getStatusMessage(int status) {
        switch (status) {
            case 0: return "验证成功";
            case 21000: return "App Store无法读取提供的JSON对象";
            case 21002: return "receipt-data属性中的数据格式错误或服务遇到临时问题";
            case 21003: return "收据无法验证";
            case 21004: return "提供的共享密钥与您帐户存档的共享密钥不匹配";
            case 21005: return "收据服务器当前不可用";
            case 21006: return "该收据有效,但订阅已过期";
            case 21007: return "该收据来自沙盒环境,但已发送到生产环境进行验证";
            case 21008: return "该收据来自生产环境,但已发送到沙盒环境进行验证";
            case 21009: return "内部数据访问错误";
            case 21010: return "找不到用户帐户或已删除";
            default: return "未知错误: " + status;
        }
    }

    /**
     * Apple收据验证结果
     */
    public static class AppleReceiptVerifyResult {
        private boolean success;
        private int status;
        private String errorMessage;
        private String bundleId;
        private String applicationVersion;
        private String productId;
        private String transactionId;
        private String originalTransactionId;
        private String purchaseDate;

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public String getBundleId() {
            return bundleId;
        }

        public void setBundleId(String bundleId) {
            this.bundleId = bundleId;
        }

        public String getApplicationVersion() {
            return applicationVersion;
        }

        public void setApplicationVersion(String applicationVersion) {
            this.applicationVersion = applicationVersion;
        }

        public String getProductId() {
            return productId;
        }

        public void setProductId(String productId) {
            this.productId = productId;
        }

        public String getTransactionId() {
            return transactionId;
        }

        public void setTransactionId(String transactionId) {
            this.transactionId = transactionId;
        }

        public String getOriginalTransactionId() {
            return originalTransactionId;
        }

        public void setOriginalTransactionId(String originalTransactionId) {
            this.originalTransactionId = originalTransactionId;
        }

        public String getPurchaseDate() {
            return purchaseDate;
        }

        public void setPurchaseDate(String purchaseDate) {
            this.purchaseDate = purchaseDate;
        }
    }
}
