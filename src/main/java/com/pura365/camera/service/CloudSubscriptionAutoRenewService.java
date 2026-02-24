package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pura365.camera.domain.CloudPlan;
import com.pura365.camera.domain.CloudSubscription;
import com.pura365.camera.domain.PaymentOrder;
import com.pura365.camera.domain.UserDevice;
import com.pura365.camera.enums.EnableStatus;
import com.pura365.camera.enums.PaymentOrderStatus;
import com.pura365.camera.repository.CloudPlanRepository;
import com.pura365.camera.repository.CloudSubscriptionRepository;
import com.pura365.camera.repository.PaymentOrderRepository;
import com.pura365.camera.repository.UserDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Cloud subscription auto-renew scheduler.
 */
@Service
public class CloudSubscriptionAutoRenewService {

    private static final Logger log = LoggerFactory.getLogger(CloudSubscriptionAutoRenewService.class);

    private static final String PRODUCT_TYPE_CLOUD_STORAGE = "cloud_storage";
    private static final String PAYMENT_METHOD_AUTO_RENEW = "auto_renew";
    private static final String DEFAULT_CURRENCY = "CNY";
    private static final String USD_CURRENCY = "USD";

    @Autowired
    private CloudSubscriptionRepository cloudSubscriptionRepository;

    @Autowired
    private CloudPlanRepository cloudPlanRepository;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private UserDeviceRepository userDeviceRepository;

    @Autowired
    private PaymentCallbackService paymentCallbackService;

    @Value("${cloud.auto-renew.enabled:true}")
    private boolean autoRenewEnabled;

    @Value("${cloud.auto-renew.ahead-hours:24}")
    private int renewAheadHours;

    @Value("${cloud.auto-renew.expired-grace-hours:72}")
    private int expiredGraceHours;

    @Value("${app.payment.default-currency:CNY}")
    private String serverCurrency;

    @Scheduled(cron = "${cloud.auto-renew.cron:0 15 * * * ?}")
    public void scheduledAutoRenew() {
        if (!autoRenewEnabled) {
            log.info("Cloud auto-renew is disabled, skip this run");
            return;
        }
        executeAutoRenew();
    }

    public void executeAutoRenew() {
        Date now = new Date();
        int aheadHours = Math.max(renewAheadHours, 1);
        int graceHours = Math.max(expiredGraceHours, 0);
        Date renewBefore = Date.from(now.toInstant().plus(aheadHours, ChronoUnit.HOURS));
        Date renewAfter = Date.from(now.toInstant().minus(graceHours, ChronoUnit.HOURS));

        LambdaQueryWrapper<CloudSubscription> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CloudSubscription::getAutoRenew, 1)
                .isNotNull(CloudSubscription::getExpireAt)
                .le(CloudSubscription::getExpireAt, renewBefore)
                .ge(CloudSubscription::getExpireAt, renewAfter)
                .orderByAsc(CloudSubscription::getExpireAt);

        List<CloudSubscription> subscriptions = cloudSubscriptionRepository.selectList(wrapper);
        if (subscriptions == null || subscriptions.isEmpty()) {
            log.info("No subscriptions matched auto-renew window: aheadHours={}, graceHours={}", aheadHours, graceHours);
            return;
        }

        int successCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        for (CloudSubscription candidate : subscriptions) {
            try {
                AutoRenewResult result = autoRenewOne(candidate, now);
                if (result == AutoRenewResult.SUCCESS) {
                    successCount++;
                } else if (result == AutoRenewResult.SKIPPED) {
                    skippedCount++;
                } else {
                    failedCount++;
                }
            } catch (Exception e) {
                failedCount++;
                log.error("Auto-renew failed with exception: subscriptionId={}, deviceId={}",
                        candidate.getId(), candidate.getDeviceId(), e);
            }
        }

        log.info("Cloud auto-renew finished: total={}, success={}, skipped={}, failed={}",
                subscriptions.size(), successCount, skippedCount, failedCount);
    }

    private AutoRenewResult autoRenewOne(CloudSubscription candidate, Date now) {
        CloudSubscription latest = findLatestByDevice(candidate.getDeviceId());
        if (latest == null) {
            log.warn("Skip auto-renew: latest subscription not found, deviceId={}", candidate.getDeviceId());
            return AutoRenewResult.SKIPPED;
        }
        if (candidate.getId() != null && latest.getId() != null && !candidate.getId().equals(latest.getId())) {
            log.debug("Skip stale subscription record: candidateId={}, latestId={}, deviceId={}",
                    candidate.getId(), latest.getId(), candidate.getDeviceId());
            return AutoRenewResult.SKIPPED;
        }
        if (latest.getAutoRenew() == null || latest.getAutoRenew() != 1) {
            return AutoRenewResult.SKIPPED;
        }
        if (latest.getUserId() == null || !StringUtils.hasText(latest.getDeviceId()) || !StringUtils.hasText(latest.getPlanId())) {
            log.warn("Skip auto-renew: invalid subscription data, subscriptionId={}, userId={}, deviceId={}, planId={}",
                    latest.getId(), latest.getUserId(), latest.getDeviceId(), latest.getPlanId());
            return AutoRenewResult.FAILED;
        }
        if (!hasUserDevice(latest.getUserId(), latest.getDeviceId())) {
            log.warn("Skip auto-renew: user-device relation not found, subscriptionId={}, userId={}, deviceId={}",
                    latest.getId(), latest.getUserId(), latest.getDeviceId());
            return AutoRenewResult.SKIPPED;
        }

        CloudPlan plan = findPlanByPlanId(latest.getPlanId());
        if (plan == null) {
            log.warn("Skip auto-renew: plan not found, subscriptionId={}, planId={}", latest.getId(), latest.getPlanId());
            return AutoRenewResult.FAILED;
        }
        if (plan.getStatus() != EnableStatus.ENABLED) {
            log.warn("Skip auto-renew: plan disabled, subscriptionId={}, planId={}", latest.getId(), latest.getPlanId());
            return AutoRenewResult.SKIPPED;
        }

        PaymentOrder pendingOrder = findPendingAutoRenewOrder(latest);
        PaymentOrder targetOrder = pendingOrder != null ? pendingOrder : createAutoRenewOrder(latest, plan, now);

        String txId = generateAutoRenewTransactionId(targetOrder.getOrderId());
        boolean paid = paymentCallbackService.handlePaymentSuccess(targetOrder.getOrderId(), PAYMENT_METHOD_AUTO_RENEW, txId);
        if (paid) {
            log.info("Auto-renew success: orderId={}, deviceId={}, planId={}, oldExpireAt={}",
                    targetOrder.getOrderId(), latest.getDeviceId(), latest.getPlanId(), latest.getExpireAt());
            return AutoRenewResult.SUCCESS;
        }

        log.error("Auto-renew callback failed: orderId={}, deviceId={}, planId={}",
                targetOrder.getOrderId(), latest.getDeviceId(), latest.getPlanId());
        return AutoRenewResult.FAILED;
    }

    private CloudSubscription findLatestByDevice(String deviceId) {
        if (!StringUtils.hasText(deviceId)) {
            return null;
        }
        LambdaQueryWrapper<CloudSubscription> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CloudSubscription::getDeviceId, deviceId)
                .orderByDesc(CloudSubscription::getExpireAt)
                .last("LIMIT 1");
        return cloudSubscriptionRepository.selectOne(wrapper);
    }

    private CloudPlan findPlanByPlanId(String planId) {
        LambdaQueryWrapper<CloudPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CloudPlan::getPlanId, planId).last("LIMIT 1");
        CloudPlan plan = cloudPlanRepository.selectOne(wrapper);
        if (plan != null) {
            return plan;
        }
        try {
            return cloudPlanRepository.selectById(Long.parseLong(planId));
        } catch (Exception ignored) {
            return null;
        }
    }

    private PaymentOrder findPendingAutoRenewOrder(CloudSubscription subscription) {
        LambdaQueryWrapper<PaymentOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaymentOrder::getUserId, subscription.getUserId())
                .eq(PaymentOrder::getDeviceId, subscription.getDeviceId())
                .eq(PaymentOrder::getProductType, PRODUCT_TYPE_CLOUD_STORAGE)
                .eq(PaymentOrder::getProductId, subscription.getPlanId())
                .eq(PaymentOrder::getPaymentMethod, PAYMENT_METHOD_AUTO_RENEW)
                .eq(PaymentOrder::getStatus, PaymentOrderStatus.PENDING)
                .orderByDesc(PaymentOrder::getCreatedAt)
                .last("LIMIT 1");
        return paymentOrderRepository.selectOne(wrapper);
    }

    private PaymentOrder createAutoRenewOrder(CloudSubscription subscription, CloudPlan plan, Date now) {
        PaymentOrder order = new PaymentOrder();
        order.setOrderId(generateOrderId());
        order.setUserId(subscription.getUserId());
        order.setDeviceId(subscription.getDeviceId());
        order.setProductType(PRODUCT_TYPE_CLOUD_STORAGE);
        order.setProductId(subscription.getPlanId());
        order.setAmount(plan.getPrice() != null ? plan.getPrice() : BigDecimal.ZERO);
        order.setCurrency(resolveServerCurrency());
        order.setStatus(PaymentOrderStatus.PENDING);
        order.setPaymentMethod(PAYMENT_METHOD_AUTO_RENEW);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        paymentOrderRepository.insert(order);
        return order;
    }

    private boolean hasUserDevice(Long userId, String deviceId) {
        if (userId == null || !StringUtils.hasText(deviceId)) {
            return false;
        }
        LambdaQueryWrapper<UserDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDevice::getUserId, userId)
                .eq(UserDevice::getDeviceId, deviceId);
        return userDeviceRepository.selectCount(wrapper) > 0;
    }

    private String generateOrderId() {
        return "order_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String generateAutoRenewTransactionId(String orderId) {
        return "AUTO_RENEW_" + orderId + "_" + System.currentTimeMillis();
    }

    private String resolveServerCurrency() {
        if (USD_CURRENCY.equalsIgnoreCase(serverCurrency)) {
            return USD_CURRENCY;
        }
        return DEFAULT_CURRENCY;
    }

    private enum AutoRenewResult {
        SUCCESS,
        SKIPPED,
        FAILED
    }
}
