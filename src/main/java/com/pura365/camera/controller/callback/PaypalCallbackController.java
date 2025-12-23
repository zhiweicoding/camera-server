package com.pura365.camera.controller.callback;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pura365.camera.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * PayPal 支付回调接口
 * 
 * 这些接口不需要用户登录验证
 */
@Tag(name = "PayPal 回调", description = "PayPal 支付回调接口（无需登录）")
@RestController
@RequestMapping("/api/payment/paypal")
public class PaypalCallbackController {

    private static final Logger log = LoggerFactory.getLogger(PaypalCallbackController.class);

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * PayPal 支付成功后跳转回来
     * 
     * 用户在 PayPal 页面完成支付后，会跳转到这个 URL
     * 我们在这里完成扣款（capture）并更新订单状态
     */
    @Operation(summary = "PayPal 支付返回", description = "用户完成 PayPal 支付后的回调地址")
    @GetMapping("/return")
    public ResponseEntity<String> paypalReturn(
            @RequestParam("order_id") String orderId,
            @RequestParam(value = "token", required = false) String paypalOrderId) {

        log.info("PayPal return callback: orderId={}, paypalOrderId={}", orderId, paypalOrderId);

        boolean success = paymentService.handlePaypalReturn(orderId, paypalOrderId);

        if (success) {
            // 支付成功，返回 HTML 页面或重定向到 App
            String html = buildSuccessHtml(orderId);
            return ResponseEntity.ok()
                    .header("Content-Type", "text/html;charset=UTF-8")
                    .body(html);
        } else {
            String html = buildFailureHtml(orderId, "支付处理失败，请稍后重试");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .header("Content-Type", "text/html;charset=UTF-8")
                    .body(html);
        }
    }

    /**
     * PayPal 用户取消支付
     */
    @Operation(summary = "PayPal 支付取消", description = "用户取消 PayPal 支付后的回调地址")
    @GetMapping("/cancel")
    public ResponseEntity<String> paypalCancel(
            @RequestParam("order_id") String orderId) {

        log.info("PayPal cancel callback: orderId={}", orderId);

        String html = buildCancelHtml(orderId);
        return ResponseEntity.ok()
                .header("Content-Type", "text/html;charset=UTF-8")
                .body(html);
    }

    /**
     * PayPal Webhook 通知
     * 
     * PayPal 服务器主动推送支付状态变更通知
     * 需要在 PayPal Developer Portal 配置 Webhook URL
     */
    @Operation(summary = "PayPal Webhook", description = "接收 PayPal 服务器推送的支付通知")
    @PostMapping("/webhook")
    public ResponseEntity<String> paypalWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-ID", required = false) String transmissionId,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-TIME", required = false) String transmissionTime,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-SIG", required = false) String transmissionSig,
            @RequestHeader(value = "PAYPAL-CERT-URL", required = false) String certUrl,
            @RequestHeader(value = "PAYPAL-AUTH-ALGO", required = false) String authAlgo) {

        log.info("Received PayPal webhook: transmissionId={}", transmissionId);
        log.debug("PayPal webhook payload: {}", payload);

        try {
            // 解析事件
            JsonNode event = objectMapper.readTree(payload);
            String eventType = event.path("event_type").asText();
            
            // 获取 PayPal 订单ID
            String paypalOrderId = null;
            JsonNode resource = event.path("resource");
            
            if (resource.has("id")) {
                // CHECKOUT.ORDER.* 事件
                paypalOrderId = resource.path("id").asText();
            } else if (resource.has("supplementary_data")) {
                // PAYMENT.CAPTURE.* 事件
                paypalOrderId = resource
                        .path("supplementary_data")
                        .path("related_ids")
                        .path("order_id")
                        .asText();
            }

            if (paypalOrderId != null && !paypalOrderId.isEmpty()) {
                paymentService.handlePaypalWebhook(paypalOrderId, eventType);
            }

            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            log.error("Failed to process PayPal webhook: {}", e.getMessage(), e);
            // 仍然返回 200，避免 PayPal 重试
            return ResponseEntity.ok("ERROR");
        }
    }

    /**
     * 构建支付成功页面
     */
    private String buildSuccessHtml(String orderId) {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>支付成功</title>\n" +
                "    <style>\n" +
                "        body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; text-align: center; padding: 50px 20px; background: #f5f5f5; }\n" +
                "        .container { max-width: 400px; margin: 0 auto; background: white; padding: 40px; border-radius: 12px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }\n" +
                "        .icon { font-size: 64px; color: #4CAF50; }\n" +
                "        h1 { color: #333; margin: 20px 0 10px; }\n" +
                "        p { color: #666; }\n" +
                "        .order-id { font-size: 12px; color: #999; margin-top: 20px; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <div class=\"icon\">✓</div>\n" +
                "        <h1>支付成功</h1>\n" +
                "        <p>您的云存储服务已激活</p>\n" +
                "        <p>请返回 App 查看</p>\n" +
                "        <p class=\"order-id\">订单号: " + orderId + "</p>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";
    }

    /**
     * 构建支付失败页面
     */
    private String buildFailureHtml(String orderId, String message) {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>支付失败</title>\n" +
                "    <style>\n" +
                "        body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; text-align: center; padding: 50px 20px; background: #f5f5f5; }\n" +
                "        .container { max-width: 400px; margin: 0 auto; background: white; padding: 40px; border-radius: 12px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }\n" +
                "        .icon { font-size: 64px; color: #f44336; }\n" +
                "        h1 { color: #333; margin: 20px 0 10px; }\n" +
                "        p { color: #666; }\n" +
                "        .order-id { font-size: 12px; color: #999; margin-top: 20px; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <div class=\"icon\">✗</div>\n" +
                "        <h1>支付失败</h1>\n" +
                "        <p>" + message + "</p>\n" +
                "        <p class=\"order-id\">订单号: " + orderId + "</p>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";
    }

    /**
     * 构建支付取消页面
     */
    private String buildCancelHtml(String orderId) {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>支付取消</title>\n" +
                "    <style>\n" +
                "        body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; text-align: center; padding: 50px 20px; background: #f5f5f5; }\n" +
                "        .container { max-width: 400px; margin: 0 auto; background: white; padding: 40px; border-radius: 12px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }\n" +
                "        .icon { font-size: 64px; color: #FF9800; }\n" +
                "        h1 { color: #333; margin: 20px 0 10px; }\n" +
                "        p { color: #666; }\n" +
                "        .order-id { font-size: 12px; color: #999; margin-top: 20px; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <div class=\"icon\">!</div>\n" +
                "        <h1>支付已取消</h1>\n" +
                "        <p>您可以返回 App 重新支付</p>\n" +
                "        <p class=\"order-id\">订单号: " + orderId + "</p>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";
    }
}
