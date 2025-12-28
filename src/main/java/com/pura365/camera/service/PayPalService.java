package com.pura365.camera.service;

import com.paypal.core.PayPalHttpClient;
import com.paypal.http.HttpResponse;
import com.paypal.orders.*;
import com.pura365.camera.config.PaypalConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * PayPal 支付服务
 * 
 * 封装 PayPal Checkout API 调用
 */
@Service
public class PaypalService {

    private static final Logger log = LoggerFactory.getLogger(PaypalService.class);

    @Autowired
    private PayPalHttpClient payPalHttpClient;

    @Autowired
    private PaypalConfig paypalConfig;

    /**
     * 创建 PayPal 订单结果
     */
    public static class CreateOrderResult {
        private boolean success;
        private String paypalOrderId;
        private String approvalUrl;
        private String errorMessage;

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getPaypalOrderId() {
            return paypalOrderId;
        }

        public void setPaypalOrderId(String paypalOrderId) {
            this.paypalOrderId = paypalOrderId;
        }

        public String getApprovalUrl() {
            return approvalUrl;
        }

        public void setApprovalUrl(String approvalUrl) {
            this.approvalUrl = approvalUrl;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }

    /**
     * 捕获订单结果
     */
    public static class CaptureResult {
        private boolean success;
        private String captureId;
        private String status;
        private String errorMessage;

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getCaptureId() {
            return captureId;
        }

        public void setCaptureId(String captureId) {
            this.captureId = captureId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }

    /**
     * 创建 PayPal 订单
     *
     * @param orderId     业务订单ID（作为参考）
     * @param amount      金额
     * @param currency    货币代码（USD, CNY 等）
     * @param description 商品描述
     * @return 创建结果，包含 PayPal 订单ID 和支付链接
     */
    public CreateOrderResult createOrder(String orderId, BigDecimal amount, String currency, String description) {
        CreateOrderResult result = new CreateOrderResult();

        try {
            OrdersCreateRequest request = new OrdersCreateRequest();
            request.prefer("return=representation");
            request.requestBody(buildOrderRequest(orderId, amount, currency, description));

            HttpResponse<Order> response = payPalHttpClient.execute(request);
            Order order = response.result();

            log.info("PayPal order created: {}, status: {}", order.id(), order.status());

            result.setSuccess(true);
            result.setPaypalOrderId(order.id());

            // 获取支付链接
            for (LinkDescription link : order.links()) {
                if ("approve".equals(link.rel())) {
                    result.setApprovalUrl(link.href());
                    break;
                }
            }

        } catch (IOException e) {
            log.error("Failed to create PayPal order: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setErrorMessage("创建 PayPal 订单失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 捕获 PayPal 订单（完成支付）
     * 
     * 当用户在 PayPal 页面授权后，调用此方法完成扣款
     *
     * @param paypalOrderId PayPal 订单ID
     * @return 捕获结果
     */
    public CaptureResult captureOrder(String paypalOrderId) {
        CaptureResult result = new CaptureResult();

        try {
            OrdersCaptureRequest request = new OrdersCaptureRequest(paypalOrderId);
            request.requestBody(new OrderRequest());

            HttpResponse<Order> response = payPalHttpClient.execute(request);
            Order order = response.result();

            log.info("PayPal order captured: {}, status: {}", order.id(), order.status());

            result.setSuccess(true);
            result.setStatus(order.status());

            // 获取 Capture ID
            if (order.purchaseUnits() != null && !order.purchaseUnits().isEmpty()) {
                PurchaseUnit unit = order.purchaseUnits().get(0);
                if (unit.payments() != null && unit.payments().captures() != null 
                        && !unit.payments().captures().isEmpty()) {
                    result.setCaptureId(unit.payments().captures().get(0).id());
                }
            }

        } catch (IOException e) {
            log.error("Failed to capture PayPal order: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setErrorMessage("捕获 PayPal 订单失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 获取 PayPal 订单详情
     *
     * @param paypalOrderId PayPal 订单ID
     * @return 订单状态，失败返回 null
     */
    public String getOrderStatus(String paypalOrderId) {
        try {
            OrdersGetRequest request = new OrdersGetRequest(paypalOrderId);
            HttpResponse<Order> response = payPalHttpClient.execute(request);
            return response.result().status();
        } catch (IOException e) {
            log.error("Failed to get PayPal order status: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 构建 PayPal 订单请求体
     */
    private OrderRequest buildOrderRequest(String orderId, BigDecimal amount, String currency, String description) {
        OrderRequest orderRequest = new OrderRequest();
        orderRequest.checkoutPaymentIntent("CAPTURE");

        // 设置回调 URL
        ApplicationContext applicationContext = new ApplicationContext()
                .brandName("Pura365")
                .landingPage("BILLING")
                .cancelUrl(paypalConfig.getCancelUrl() + "?order_id=" + orderId)
                .returnUrl(paypalConfig.getReturnUrl() + "?order_id=" + orderId)
                .userAction("PAY_NOW");
        orderRequest.applicationContext(applicationContext);

        // 设置商品信息
        List<PurchaseUnitRequest> purchaseUnits = new ArrayList<>();
        PurchaseUnitRequest purchaseUnit = new PurchaseUnitRequest()
                .referenceId(orderId)
                .description(description)
                .amountWithBreakdown(new AmountWithBreakdown()
                        .currencyCode(currency)
                        .value(amount.setScale(2, BigDecimal.ROUND_HALF_UP).toString()));
        purchaseUnits.add(purchaseUnit);
        orderRequest.purchaseUnits(purchaseUnits);

        return orderRequest;
    }
}
