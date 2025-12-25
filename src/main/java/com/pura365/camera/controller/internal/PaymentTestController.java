package com.pura365.camera.controller.internal;

import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.model.payment.CreateOrderRequest;
import com.pura365.camera.model.payment.OrderVO;
import com.pura365.camera.service.PaymentCallbackService;
import com.pura365.camera.service.PaymentService;
import com.pura365.camera.service.PaymentService.CreateOrderResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/**
 * 支付测试接口
 * 
 * 用于在支付渠道未接入的情况下测试支付成功后的业务流程：
 * - 测试订单生成是否正确
 * - 测试报表数据是否正确
 * - 测试导出功能是否正常
 * 
 * 注意：此接口仅用于测试环境，生产环境应禁用
 */
@Tag(name = "支付测试", description = "支付测试接口，用于模拟支付成功")
@RestController
@RequestMapping("/api/internal/payment-test")
public class PaymentTestController {

    private static final Logger log = LoggerFactory.getLogger(PaymentTestController.class);

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentCallbackService paymentCallbackService;

    /**
     * 是否启用测试接口，默认启用
     * 生产环境可设置 payment.test.enabled=false 来禁用
     */
    @Value("${payment.test.enabled:true}")
    private boolean testEnabled;

    /**
     * 创建订单并直接模拟支付成功
     * 
     * 一步完成：创建订单 -> 模拟支付成功 -> 激活云存储
     */
    @Operation(summary = "创建订单并模拟支付成功", 
               description = "一步完成创建订单并模拟支付成功，用于测试支付成功后的业务流程（订单、报表、导出等）")
    @PostMapping("/create-and-pay")
    public ApiResponse<MockPaymentResult> createAndPay(
            @RequestAttribute("currentUserId") Long currentUserId,
            @RequestBody CreateOrderRequest request) {

        if (!testEnabled) {
            return ApiResponse.error(403, "测试接口已禁用");
        }

        log.info("【测试】创建订单并模拟支付: userId={}, deviceId={}, productId={}", 
                currentUserId, request.getDeviceId(), request.getProductId());

        // 1. 创建订单
        CreateOrderResult orderResult = paymentService.createOrder(currentUserId, request);
        if (!orderResult.isSuccess()) {
            return ApiResponse.error(orderResult.getErrorCode(), orderResult.getErrorMessage());
        }

        String orderId = orderResult.getOrder().getOrderId();

        // 2. 模拟支付成功
        String mockTransactionId = "TEST_" + System.currentTimeMillis();
        String paymentMethod = request.getPaymentMethod() != null ? request.getPaymentMethod() : "wechat";
        
        boolean success = paymentCallbackService.handlePaymentSuccess(orderId, paymentMethod, mockTransactionId);

        // 3. 构建返回结果
        MockPaymentResult result = new MockPaymentResult();
        result.setOrderId(orderId);
        result.setAmount(orderResult.getOrder().getAmount());
        result.setCurrency(orderResult.getOrder().getCurrency());
        result.setPaymentMethod(paymentMethod);
        result.setMockTransactionId(mockTransactionId);
        result.setPaymentSuccess(success);
        result.setMessage(success ? "模拟支付成功，已激活云存储" : "模拟支付处理失败，请检查日志");

        log.info("【测试】模拟支付完成: orderId={}, success={}", orderId, success);

        return ApiResponse.success(result);
    }

    /**
     * 对已有订单模拟支付成功
     * 
     * 适用于已创建订单但未支付的场景
     */
    @Operation(summary = "模拟订单支付成功", 
               description = "对已存在的待支付订单模拟支付成功")
    @PostMapping("/mock-pay/{orderId}")
    public ApiResponse<MockPaymentResult> mockPay(
            @Parameter(description = "订单ID") @PathVariable("orderId") String orderId,
            @Parameter(description = "支付方式（wechat/paypal/apple/google）") 
            @RequestParam(value = "payment_method", defaultValue = "wechat") String paymentMethod) {

        if (!testEnabled) {
            return ApiResponse.error(403, "测试接口已禁用");
        }

        log.info("【测试】模拟订单支付成功: orderId={}, paymentMethod={}", orderId, paymentMethod);

        // 模拟支付成功
        String mockTransactionId = "TEST_" + System.currentTimeMillis();
        boolean success = paymentCallbackService.handlePaymentSuccess(orderId, paymentMethod, mockTransactionId);

        MockPaymentResult result = new MockPaymentResult();
        result.setOrderId(orderId);
        result.setPaymentMethod(paymentMethod);
        result.setMockTransactionId(mockTransactionId);
        result.setPaymentSuccess(success);
        result.setMessage(success ? "模拟支付成功" : "模拟支付失败，订单可能不存在或已支付");

        return ApiResponse.success(result);
    }

    /**
     * 批量创建测试订单并支付
     * 
     * 用于生成多条测试数据以验证报表和导出功能
     */
    @Operation(summary = "批量创建测试订单", 
               description = "批量创建订单并模拟支付成功，用于测试报表和导出功能")
    @PostMapping("/batch-create")
    public ApiResponse<BatchMockResult> batchCreate(
            @RequestAttribute("currentUserId") Long currentUserId,
            @RequestBody BatchCreateRequest request) {

        if (!testEnabled) {
            return ApiResponse.error(403, "测试接口已禁用");
        }

        int count = request.getCount() != null ? request.getCount() : 1;
        if (count > 100) {
            count = 100; // 限制最多100条
        }

        log.info("【测试】批量创建测试订单: userId={}, deviceId={}, count={}", 
                currentUserId, request.getDeviceId(), count);

        int successCount = 0;
        int failCount = 0;

        for (int i = 0; i < count; i++) {
            CreateOrderRequest orderRequest = new CreateOrderRequest();
            orderRequest.setProductType("cloud_storage");
            orderRequest.setProductId(request.getProductId() != null ? request.getProductId() : "plan_7day");
            orderRequest.setDeviceId(request.getDeviceId());
            orderRequest.setPaymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : "wechat");

            CreateOrderResult orderResult = paymentService.createOrder(currentUserId, orderRequest);
            if (orderResult.isSuccess()) {
                String orderId = orderResult.getOrder().getOrderId();
                String mockTransactionId = "TEST_BATCH_" + System.currentTimeMillis() + "_" + i;
                boolean paySuccess = paymentCallbackService.handlePaymentSuccess(
                        orderId, orderRequest.getPaymentMethod(), mockTransactionId);
                if (paySuccess) {
                    successCount++;
                } else {
                    failCount++;
                }
            } else {
                failCount++;
            }

            // 避免订单ID重复，稍微延迟
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {
            }
        }

        BatchMockResult result = new BatchMockResult();
        result.setTotalRequested(count);
        result.setSuccessCount(successCount);
        result.setFailCount(failCount);
        result.setMessage(String.format("批量创建完成：成功 %d 条，失败 %d 条", successCount, failCount));

        log.info("【测试】批量创建完成: success={}, fail={}", successCount, failCount);

        return ApiResponse.success(result);
    }

    /**
     * 检查测试接口是否启用
     */
    @Operation(summary = "检查测试接口状态", description = "检查支付测试接口是否可用")
    @GetMapping("/status")
    public ApiResponse<TestStatusResult> getStatus() {
        TestStatusResult result = new TestStatusResult();
        result.setEnabled(testEnabled);
        result.setMessage(testEnabled ? "测试接口已启用" : "测试接口已禁用（生产环境）");
        return ApiResponse.success(result);
    }

    // ===== 内部类：响应对象 =====

    public static class MockPaymentResult {
        private String orderId;
        private java.math.BigDecimal amount;
        private String currency;
        private String paymentMethod;
        private String mockTransactionId;
        private boolean paymentSuccess;
        private String message;

        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        public java.math.BigDecimal getAmount() { return amount; }
        public void setAmount(java.math.BigDecimal amount) { this.amount = amount; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
        public String getMockTransactionId() { return mockTransactionId; }
        public void setMockTransactionId(String mockTransactionId) { this.mockTransactionId = mockTransactionId; }
        public boolean isPaymentSuccess() { return paymentSuccess; }
        public void setPaymentSuccess(boolean paymentSuccess) { this.paymentSuccess = paymentSuccess; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class BatchCreateRequest {
        private String deviceId;
        private String productId;
        private String paymentMethod;
        private Integer count;

        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }
        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
        public Integer getCount() { return count; }
        public void setCount(Integer count) { this.count = count; }
    }

    public static class BatchMockResult {
        private int totalRequested;
        private int successCount;
        private int failCount;
        private String message;

        public int getTotalRequested() { return totalRequested; }
        public void setTotalRequested(int totalRequested) { this.totalRequested = totalRequested; }
        public int getSuccessCount() { return successCount; }
        public void setSuccessCount(int successCount) { this.successCount = successCount; }
        public int getFailCount() { return failCount; }
        public void setFailCount(int failCount) { this.failCount = failCount; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class TestStatusResult {
        private boolean enabled;
        private String message;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
