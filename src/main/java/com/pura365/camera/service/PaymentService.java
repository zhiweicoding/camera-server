package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pura365.camera.domain.*;
import com.pura365.camera.enums.EnableStatus;
import com.pura365.camera.enums.PaymentOrderStatus;
import com.pura365.camera.model.payment.*;
import com.pura365.camera.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.UUID;

/**
 * 支付服务
 * 
 * 处理订单创建、支付渠道对接等业务逻辑
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    /** 默认支付方式 */
    private static final String DEFAULT_PAYMENT_METHOD = "wechat";

    /** 默认货币 */
    private static final String DEFAULT_CURRENCY = "CNY";
    private static final String USD_CURRENCY = "USD";

    /** 商品类型: 云存储 */
    private static final String PRODUCT_TYPE_CLOUD_STORAGE = "cloud_storage";

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private PaymentWechatRepository paymentWechatRepository;

    @Autowired
    private CloudPlanRepository cloudPlanRepository;

    @Autowired
    private UserDeviceRepository userDeviceRepository;

    @Autowired
    private ManufacturedDeviceRepository manufacturedDeviceRepository;

    @Autowired
    private InstallerRepository installerRepository;

    @Autowired
    private DealerRepository dealerRepository;

    @Autowired
    private PaypalService paypalService;

    @Autowired
    private CommissionCalculateService commissionCalculateService;

    @Value("${app.payment.default-currency:CNY}")
    private String serverCurrency;

    /**
     * 创建支付订单
     *
     * @param userId  用户ID
     * @param request 创建订单请求
     * @return 订单信息，失败返回 null 并设置 errorMessage
     */
    public CreateOrderResult createOrder(Long userId, CreateOrderRequest request) {
        CreateOrderResult result = new CreateOrderResult();

        // 参数校验
        if (!StringUtils.hasText(request.getProductType()) || !StringUtils.hasText(request.getProductId())) {
            result.setErrorCode(400);
            result.setErrorMessage("product_type 和 product_id 不能为空");
            return result;
        }
        if (!StringUtils.hasText(request.getDeviceId())) {
            result.setErrorCode(400);
            result.setErrorMessage("device_id 不能为空");
            return result;
        }

        // 校验设备归属
        if (!hasUserDevice(userId, request.getDeviceId())) {
            result.setErrorCode(403);
            result.setErrorMessage("无权操作该设备");
            return result;
        }

        // 根据商品类型获取价格
        BigDecimal amount;
        String currency = resolveServerCurrency();
        CloudPlan plan = null;
        if (PRODUCT_TYPE_CLOUD_STORAGE.equals(request.getProductType())) {
            plan = findPlanByPlanId(request.getProductId());
            if (plan == null) {
                result.setErrorCode(404);
                result.setErrorMessage("云存储套餐不存在");
                return result;
            }
            if (plan.getStatus() != EnableStatus.ENABLED) {
                result.setErrorCode(400);
                result.setErrorMessage("套餐已下架，暂不可购买");
                return result;
            }
            amount = plan.getPrice() != null ? plan.getPrice() : BigDecimal.ZERO;
        } else {
            result.setErrorCode(400);
            result.setErrorMessage("暂不支持的商品类型: " + request.getProductType());
            return result;
        }

        // 订单复用：检查是否有同样的待支付订单
        PaymentOrder existingOrder = findPendingOrder(userId, request.getDeviceId(), 
                request.getProductType(), request.getProductId());
        if (existingOrder != null) {
            log.info("复用已有订单: orderId={}", existingOrder.getOrderId());
            OrderVO vo = new OrderVO();
            vo.setOrderId(existingOrder.getOrderId());
            vo.setAmount(existingOrder.getAmount());
            vo.setCurrency(existingOrder.getCurrency());
            vo.setCreatedAt(formatIsoTime(existingOrder.getCreatedAt()));
            result.setSuccess(true);
            result.setOrder(vo);
            return result;
        }

        // 关闭该用户该设备的其他待支付订单（换了套餐）
        closeOtherPendingOrders(userId, request.getDeviceId());

        // 创建新订单
        String paymentMethod = StringUtils.hasText(request.getPaymentMethod())
                ? request.getPaymentMethod() : DEFAULT_PAYMENT_METHOD;

        PaymentOrder order = new PaymentOrder();
        order.setOrderId(generateOrderId());
        order.setUserId(userId);
        order.setDeviceId(request.getDeviceId());
        order.setProductType(request.getProductType());
        order.setProductId(request.getProductId());
        order.setAmount(amount);
        order.setCurrency(currency);
        order.setStatus(PaymentOrderStatus.PENDING);
        order.setPaymentMethod(paymentMethod);
        order.setCreatedAt(new Date());

        // 快照经销商/业务员信息
        snapshotVendorAndSalesmanInfo(order, request.getDeviceId());

        paymentOrderRepository.insert(order);
        log.info("创建新订单: orderId={}, userId={}, deviceId={}, productId={}", 
                order.getOrderId(), userId, request.getDeviceId(), request.getProductId());

        // 构建响应
        OrderVO vo = new OrderVO();
        vo.setOrderId(order.getOrderId());
        vo.setAmount(order.getAmount());
        vo.setCurrency(order.getCurrency());
        vo.setCreatedAt(formatIsoTime(order.getCreatedAt()));

        result.setSuccess(true);
        result.setOrder(vo);
        return result;
    }

    /**
     * 查找待支付的相同订单（用于复用）
     */
    private PaymentOrder findPendingOrder(Long userId, String deviceId, String productType, String productId) {
        LambdaQueryWrapper<PaymentOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaymentOrder::getUserId, userId)
               .eq(PaymentOrder::getDeviceId, deviceId)
               .eq(PaymentOrder::getProductType, productType)
               .eq(PaymentOrder::getProductId, productId)
               .eq(PaymentOrder::getStatus, PaymentOrderStatus.PENDING)
               .last("LIMIT 1");
        return paymentOrderRepository.selectOne(wrapper);
    }

    private void fillOrderCommissionSafely(PaymentOrder order, String scene) {
        try {
            commissionCalculateService.fillOrderCommission(order, order.getDeviceId());
        } catch (Exception e) {
            log.error("分润计算失败: scene={}, orderId={}", scene, order != null ? order.getOrderId() : null, e);
        }
    }

    /**
     * 关闭用户在该设备上的其他待支付订单（换套餐时调用）
     */
    private void closeOtherPendingOrders(Long userId, String deviceId) {
        LambdaQueryWrapper<PaymentOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaymentOrder::getUserId, userId)
               .eq(PaymentOrder::getDeviceId, deviceId)
               .eq(PaymentOrder::getStatus, PaymentOrderStatus.PENDING);
        
        PaymentOrder update = new PaymentOrder();
        update.setStatus(PaymentOrderStatus.CANCELLED);
        update.setUpdatedAt(new Date());
        
        int count = paymentOrderRepository.update(update, wrapper);
        if (count > 0) {
            log.info("关闭旧订单: userId={}, deviceId={}, count={}", userId, deviceId, count);
        }
    }

    /**
     * 查询订单状态
     *
     * @param userId  用户ID
     * @param orderId 订单ID
     * @return 订单信息，不存在或无权限返回 null
     */
    public OrderVO getOrderStatus(Long userId, String orderId) {
        PaymentOrder order = getOrderByIdAndUser(orderId, userId);
        if (order == null) {
            return null;
        }

        OrderVO vo = new OrderVO();
        vo.setOrderId(order.getOrderId());
        vo.setStatus(order.getStatus() != null ? order.getStatus().getCode() : null);
        vo.setAmount(order.getAmount());
        vo.setPaidAt(order.getPaidAt() != null ? formatIsoTime(order.getPaidAt()) : null);
        return vo;
    }

    /**
     * 发起微信支付
     * 
     * @param userId  用户ID
     * @param orderId 订单ID
     * @return 微信支付参数，订单不存在返回 null
     */
    public WechatPayVO wechatPay(Long userId, String orderId) {
        PaymentOrder order = getOrderByIdAndUser(orderId, userId);
        if (order == null) {
            return null;
        }

        // 校验订单状态：只有待支付的订单才能发起支付
        if (PaymentOrderStatus.PENDING != order.getStatus()) {
            log.warn("订单状态不允许支付: orderId={}, status={}", orderId, order.getStatus());
            return null;
        }

        // 创建微信预支付记录 (mock)
        PaymentWechat pw = new PaymentWechat();
        pw.setOrderId(order.getOrderId());
        pw.setPrepayId("mock_prepay_" + order.getOrderId());
        pw.setRawResponse("{}");
        pw.setCreatedAt(new Date());
        paymentWechatRepository.insert(pw);

        // 返回支付参数
        WechatPayVO vo = new WechatPayVO();
        vo.setAppid("wx_app_id_mock");
        vo.setPartnerid("partner_mock");
        vo.setPrepayid(pw.getPrepayId());
        vo.setPackageValue("Sign=WXPay");
        vo.setNoncestr(UUID.randomUUID().toString().replace("-", ""));
        vo.setTimestamp(String.valueOf(System.currentTimeMillis() / 1000));
        vo.setSign("mock_sign_" + order.getOrderId());
        return vo;
    }

    /**
     * 发起 PayPal 支付
     *
     * @param userId  用户ID
     * @param orderId 订单ID
     * @return PayPal 支付参数，订单不存在返回 null
     */
    public PaypalPayVO paypalPay(Long userId, String orderId) {
        PaymentOrder order = getOrderByIdAndUser(orderId, userId);
        if (order == null) {
            return null;
        }

        // 校验订单状态：只有待支付的订单才能发起支付
        if (PaymentOrderStatus.PENDING != order.getStatus()) {
            log.warn("订单状态不允许支付: orderId={}, status={}", orderId, order.getStatus());
            return null;
        }

        // 如果已有 PayPal 订单ID，直接返回
        if (StringUtils.hasText(order.getThirdOrderId()) && order.getThirdOrderId().startsWith("paypal_")) {
            String paypalOrderId = order.getThirdOrderId().substring(7);
            String status = paypalService.getOrderStatus(paypalOrderId);
            if ("CREATED".equals(status) || "APPROVED".equals(status)) {
                PaypalPayVO vo = new PaypalPayVO();
                vo.setPaypalOrderId(paypalOrderId);
                vo.setApprovalUrl("https://www.sandbox.paypal.com/checkoutnow?token=" + paypalOrderId);
                return vo;
            }
        }

        // 获取商品描述
        String description = "Pura365 云存储服务";
        if (PRODUCT_TYPE_CLOUD_STORAGE.equals(order.getProductType())) {
            CloudPlan plan = findPlanByPlanId(order.getProductId());
            if (plan != null) {
                description = plan.getName();
            }
        }

        // 创建 PayPal 订单，使用 USD 货币
        BigDecimal usdAmount = convertToUsd(order.getAmount(), order.getCurrency());
        PaypalService.CreateOrderResult result = paypalService.createOrder(
                order.getOrderId(),
                usdAmount,
                "USD",
                description
        );

        if (!result.isSuccess()) {
            log.error("Failed to create PayPal order for {}: {}", orderId, result.getErrorMessage());
            return null;
        }

        // 保存 PayPal 订单ID
        order.setThirdOrderId("paypal_" + result.getPaypalOrderId());
        order.setUpdatedAt(new Date());
        paymentOrderRepository.updateById(order);

        PaypalPayVO vo = new PaypalPayVO();
        vo.setPaypalOrderId(result.getPaypalOrderId());
        vo.setApprovalUrl(result.getApprovalUrl());
        return vo;
    }

    /**
     * 处理 PayPal 支付回调（用户授权后）
     *
     * @param orderId       业务订单ID
     * @param paypalOrderId PayPal 订单ID
     * @return 处理结果
     */
    public boolean handlePaypalReturn(String orderId, String paypalOrderId) {
        PaymentOrder order = findOrderByOrderId(orderId);
        if (order == null) {
            log.warn("PayPal return: order not found: {}", orderId);
            return false;
        }

        // 校验订单状态：防止重复处理
        if (PaymentOrderStatus.PAID == order.getStatus()) {
            log.info("订单已支付，跳过处理: orderId={}", orderId);
            return true;
        }
        if (PaymentOrderStatus.PENDING != order.getStatus()) {
            log.warn("订单状态不允许支付: orderId={}, status={}", orderId, order.getStatus());
            return false;
        }

        // 验证 PayPal 订单ID 匹配
        if (!StringUtils.hasText(order.getThirdOrderId()) 
                || !order.getThirdOrderId().equals("paypal_" + paypalOrderId)) {
            log.warn("PayPal return: order id mismatch, expected: {}, got: {}", 
                    order.getThirdOrderId(), "paypal_" + paypalOrderId);
            return false;
        }

        // 捕获订单（完成扣款）
        PaypalService.CaptureResult captureResult = paypalService.captureOrder(paypalOrderId);
        if (!captureResult.isSuccess()) {
            log.error("Failed to capture PayPal order {}: {}", paypalOrderId, captureResult.getErrorMessage());
            return false;
        }

        // 更新订单状态
        if ("COMPLETED".equals(captureResult.getStatus())) {
            order.setStatus(PaymentOrderStatus.PAID);
            order.setPaidAt(new Date());
            order.setUpdatedAt(new Date());
            fillOrderCommissionSafely(order, "paypal_return");
            paymentOrderRepository.updateById(order);

            // 激活云存储服务
            activateCloudService(order);

            log.info("PayPal payment completed for order: {}", orderId);
            return true;
        }

        return false;
    }

    /**
     * 处理 PayPal Webhook 通知
     *
     * @param paypalOrderId PayPal 订单ID
     * @param eventType     事件类型
     */
    public void handlePaypalWebhook(String paypalOrderId, String eventType) {
        log.info("Received PayPal webhook: {}, event: {}", paypalOrderId, eventType);

        if (!"PAYMENT.CAPTURE.COMPLETED".equals(eventType)) {
            return;
        }

        // 根据 PayPal 订单ID 查找业务订单
        LambdaQueryWrapper<PaymentOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaymentOrder::getThirdOrderId, "paypal_" + paypalOrderId).last("LIMIT 1");
        PaymentOrder order = paymentOrderRepository.selectOne(wrapper);

        if (order == null) {
            log.warn("PayPal webhook: order not found for PayPal order: {}", paypalOrderId);
            return;
        }

        if (PaymentOrderStatus.PAID == order.getStatus()) {
            log.info("Order {} already paid, skip webhook", order.getOrderId());
            return;
        }

        // 更新订单状态
        order.setStatus(PaymentOrderStatus.PAID);
        order.setPaidAt(new Date());
        order.setUpdatedAt(new Date());
        fillOrderCommissionSafely(order, "paypal_webhook");
        paymentOrderRepository.updateById(order);

        // 激活云存储服务
        activateCloudService(order);

        log.info("Order {} marked as paid via webhook", order.getOrderId());
    }

    /**
     * 激活云存储服务
     */
    private void activateCloudService(PaymentOrder order) {
        // TODO: 实现云存储套餐激活逻辑
        // 1. 根据 order.getProductId() 获取套餐信息
        // 2. 为 order.getDeviceId() 创建或延长云存储订阅
        log.info("Activating cloud service for device: {}, plan: {}", 
                order.getDeviceId(), order.getProductId());
    }

    /**
     * CNY 转 USD（简单汇率转换）
     */
    private BigDecimal convertToUsd(BigDecimal amount, String currency) {
        if ("USD".equalsIgnoreCase(currency)) {
            return amount;
        }
        // 简单汇率：1 USD = 7.2 CNY
        if ("CNY".equalsIgnoreCase(currency)) {
            return amount.divide(new BigDecimal("7.2"), 2, BigDecimal.ROUND_HALF_UP);
        }
        return amount;
    }

    private String resolveServerCurrency() {
        if (USD_CURRENCY.equalsIgnoreCase(serverCurrency)) {
            return USD_CURRENCY;
        }
        return DEFAULT_CURRENCY;
    }

    /**
     * Apple Pay 支付
     *
     * @param userId  用户ID
     * @param request Apple Pay 请求
     * @return 支付结果，订单不存在返回 null
     */
    public ApplePayVO applePay(Long userId, ApplePayRequest request) {
        PaymentOrder order = getOrderByIdAndUser(request.getOrderId(), userId);
        if (order == null) {
            return null;
        }

        // 校验订单状态：只有待支付的订单才能发起支付
        if (PaymentOrderStatus.PENDING != order.getStatus()) {
            log.warn("订单状态不允许支付: orderId={}, status={}", request.getOrderId(), order.getStatus());
            return null;
        }

        // 模拟支付成功，更新订单状态
        order.setStatus(PaymentOrderStatus.PAID);
        order.setThirdOrderId("apple_" + order.getOrderId());
        order.setPaidAt(new Date());
        order.setUpdatedAt(new Date());
        fillOrderCommissionSafely(order, "apple_pay");
        paymentOrderRepository.updateById(order);

        ApplePayVO vo = new ApplePayVO();
        vo.setTransactionId(order.getThirdOrderId());
        vo.setStatus("completed");
        return vo;
    }

    // ============== 私有方法 ==============

    /**
     * 根据订单ID和用户ID获取订单（权限校验）
     */
    private PaymentOrder getOrderByIdAndUser(String orderId, Long userId) {
        PaymentOrder order = findOrderByOrderId(orderId);
        if (order == null || order.getUserId() == null || !order.getUserId().equals(userId)) {
            return null;
        }
        return order;
    }

    /**
     * 根据订单ID查询订单
     */
    private PaymentOrder findOrderByOrderId(String orderId) {
        LambdaQueryWrapper<PaymentOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaymentOrder::getOrderId, orderId).last("LIMIT 1");
        return paymentOrderRepository.selectOne(wrapper);
    }

    /**
     * 根据套餐ID查询云存储套餐
     */
    private CloudPlan findPlanByPlanId(String planId) {
        // 先按业务 planId 查询
        LambdaQueryWrapper<CloudPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CloudPlan::getPlanId, planId).last("LIMIT 1");
        CloudPlan plan = cloudPlanRepository.selectOne(wrapper);

        // 如果找不到，尝试按数据库主键查询
        if (plan == null) {
            try {
                Long id = Long.parseLong(planId);
                plan = cloudPlanRepository.selectById(id);
            } catch (NumberFormatException ignored) {
            }
        }
        return plan;
    }

    /**
     * 检查用户是否拥有设备
     */
    private boolean hasUserDevice(Long userId, String deviceId) {
        LambdaQueryWrapper<UserDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDevice::getUserId, userId)
               .eq(UserDevice::getDeviceId, deviceId);
        return paymentOrderRepository.selectCount(
                new LambdaQueryWrapper<PaymentOrder>().apply("1=0")) >= 0
                && userDeviceRepository.selectCount(wrapper) > 0;
    }

    /**
     * 生成订单ID
     */
    private String generateOrderId() {
        return "order_" + System.currentTimeMillis();
    }

    /**
     * 快照装机商/经销商分润信息到订单
     * 
     * 字段对应：
     * - 装机商: installer_id, installer_code, installer_rate, installer_amount
     * - 经销商: dealer_id, dealer_code, dealer_rate, dealer_amount
     * 
     * 分润比例从设备表读取（installer_commission_rate, dealer_commission_rate）
     */
    private void snapshotVendorAndSalesmanInfo(PaymentOrder order, String deviceId) {
        // 根据设备ID获取生产设备信息
        LambdaQueryWrapper<ManufacturedDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ManufacturedDevice::getDeviceId, deviceId).last("LIMIT 1");
        ManufacturedDevice device = manufacturedDeviceRepository.selectOne(wrapper);
        
        if (device == null) {
            log.warn("设备不存在，无法快照分润信息: deviceId={}", deviceId);
            return;
        }
        
        BigDecimal orderAmount = order.getAmount();
        if (orderAmount == null || orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // ========== 装机商信息 ==========
        order.setInstallerId(device.getInstallerId());
        
        // 从 Installer 表获取装机商代码
        if (device.getInstallerId() != null) {
            Installer installer = installerRepository.selectById(device.getInstallerId());
            if (installer != null) {
                order.setInstallerCode(installer.getInstallerCode());
            }
        }
        
        // 装机商分润比例和金额
        BigDecimal installerRate = device.getInstallerCommissionRate();
        order.setInstallerRate(installerRate != null ? installerRate : BigDecimal.ZERO);
        if (installerRate != null && installerRate.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal installerAmount = orderAmount
                    .multiply(installerRate)
                    .divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP);
            order.setInstallerAmount(installerAmount);
        } else {
            order.setInstallerAmount(BigDecimal.ZERO);
        }

        // ========== 经销商信息 ==========
        order.setDealerId(device.getCurrentDealerId());
        
        // 从 Dealer 表获取经销商代码
        if (device.getCurrentDealerId() != null) {
            Dealer dealer = dealerRepository.selectById(device.getCurrentDealerId());
            if (dealer != null) {
                order.setDealerCode(dealer.getDealerCode());
            }
        }
        
        // 经销商分润比例和金额
        BigDecimal dealerRate = device.getDealerCommissionRate();
        order.setDealerRate(dealerRate != null ? dealerRate : BigDecimal.ZERO);
        if (dealerRate != null && dealerRate.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal dealerAmount = orderAmount
                    .multiply(dealerRate)
                    .divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP);
            order.setDealerAmount(dealerAmount);
        } else {
            order.setDealerAmount(BigDecimal.ZERO);
        }

        // ========== 其他信息 ==========
        // 设备上线国家
        order.setOnlineCountry(device.getCountry());
        
        log.info("快照分润信息: deviceId={}, installerId={}, installerCode={}, installerRate={}, installerAmount={}, " +
                "dealerId={}, dealerCode={}, dealerRate={}, dealerAmount={}",
                deviceId, order.getInstallerId(), order.getInstallerCode(), order.getInstallerRate(), order.getInstallerAmount(),
                order.getDealerId(), order.getDealerCode(), order.getDealerRate(), order.getDealerAmount());
    }

    /**
     * 格式化时间为 ISO 8601 格式
     */
    private String formatIsoTime(Date date) {
        if (date == null) return null;
        return ISO_FORMATTER.format(date.toInstant());
    }

    /**
     * 创建订单结果
     */
    public static class CreateOrderResult {
        private boolean success;
        private OrderVO order;
        private int errorCode;
        private String errorMessage;

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public OrderVO getOrder() {
            return order;
        }

        public void setOrder(OrderVO order) {
            this.order = order;
        }

        public int getErrorCode() {
            return errorCode;
        }

        public void setErrorCode(int errorCode) {
            this.errorCode = errorCode;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }
}
