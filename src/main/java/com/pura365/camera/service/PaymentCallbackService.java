package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pura365.camera.domain.CloudPlan;
import com.pura365.camera.domain.CloudSubscription;
import com.pura365.camera.domain.Device;
import com.pura365.camera.domain.PaymentOrder;
import com.pura365.camera.enums.PaymentOrderStatus;
import com.pura365.camera.repository.CloudPlanRepository;
import com.pura365.camera.repository.CloudSubscriptionRepository;
import com.pura365.camera.repository.DeviceRepository;
import com.pura365.camera.repository.PaymentOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.Date;

/**
 * 支付回调统一处理服务
 * 
 * 处理支付成功后的业务逻辑,包括:
 * - 更新订单状态
 * - 计算并填充分佣信息
 * - 激活云存储套餐
 * - 更新设备配置
 */
@Service
public class PaymentCallbackService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentCallbackService.class);

    private final PaymentOrderRepository paymentOrderRepository;
    private final CloudSubscriptionRepository cloudSubscriptionRepository;
    private final DeviceRepository deviceRepository;
    private final CloudPlanRepository cloudPlanRepository;
    
    @Autowired
    private CommissionCalculateService commissionCalculateService;

    public PaymentCallbackService(PaymentOrderRepository paymentOrderRepository,
                                   CloudSubscriptionRepository cloudSubscriptionRepository,
                                   DeviceRepository deviceRepository,
                                   CloudPlanRepository cloudPlanRepository) {
        this.paymentOrderRepository = paymentOrderRepository;
        this.cloudSubscriptionRepository = cloudSubscriptionRepository;
        this.deviceRepository = deviceRepository;
        this.cloudPlanRepository = cloudPlanRepository;
    }

    /**
     * 处理支付成功回调
     *
     * @param orderId       订单ID
     * @param paymentMethod 支付方式
     * @param transactionId 第三方交易ID
     * @return 是否处理成功
     */
    @Transactional
    public boolean handlePaymentSuccess(String orderId, String paymentMethod, String transactionId) {
        logger.info("处理支付成功回调: orderId={}, paymentMethod={}, transactionId={}", 
                orderId, paymentMethod, transactionId);

        // 查询订单
        LambdaQueryWrapper<PaymentOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaymentOrder::getOrderId, orderId);
        PaymentOrder order = paymentOrderRepository.selectOne(wrapper);

        if (order == null) {
            logger.error("订单不存在: orderId={}", orderId);
            return false;
        }

        // 防止重复处理
        if (PaymentOrderStatus.PAID == order.getStatus()) {
            logger.warn("订单已支付,跳过处理: orderId={}", orderId);
            return true;
        }

        // 更新订单状态
        order.setStatus(PaymentOrderStatus.PAID);
        order.setThirdOrderId(transactionId);
        order.setPaidAt(new Date());
        order.setUpdatedAt(new Date());
        
        // 计算并填充分佣信息
        try {
            commissionCalculateService.fillOrderCommission(order, order.getDeviceId());
            logger.info("分佣计算完成: orderId={}, installerAmount={}, dealerAmount={}", 
                    orderId, order.getInstallerAmount(), order.getDealerAmount());
        } catch (Exception e) {
            logger.error("分佣计算失败: orderId={}", orderId, e);
            // 分佣计算失败不影响支付成功流程
        }
        
        paymentOrderRepository.updateById(order);

        // 根据商品类型处理业务逻辑
        boolean success = false;
        if ("cloud_storage".equals(order.getProductType())) {
            success = activateCloudStorage(order);
        }

        if (success) {
            logger.info("支付回调处理成功: orderId={}", orderId);
        } else {
            logger.error("支付回调处理失败: orderId={}", orderId);
        }

        return success;
    }

    /**
     * 激活云存储套餐
     */
    private boolean activateCloudStorage(PaymentOrder order) {
        try {
            // 查询套餐信息以获取周期
            CloudPlan plan = null;
            if (order.getProductId() != null) {
                LambdaQueryWrapper<CloudPlan> planWrapper = new LambdaQueryWrapper<>();
                planWrapper.eq(CloudPlan::getPlanId, order.getProductId());
                plan = cloudPlanRepository.selectOne(planWrapper);
            }

            // 查询现有订阅，如果存在则延长有效期（按到期时间降序取最新的一条）
            LambdaQueryWrapper<CloudSubscription> subWrapper = new LambdaQueryWrapper<>();
            subWrapper.eq(CloudSubscription::getDeviceId, order.getDeviceId())
                    .eq(CloudSubscription::getUserId, order.getUserId())
                    .orderByDesc(CloudSubscription::getExpireAt)
                    .last("LIMIT 1");
            CloudSubscription existingSubscription = cloudSubscriptionRepository.selectOne(subWrapper);

            // 计算到期时间：根据套餐周期
            Date baseTime;
            if (existingSubscription != null && existingSubscription.getExpireAt() != null 
                    && existingSubscription.getExpireAt().after(new Date())) {
                // 如果现有订阅未过期，从现有到期时间开始延长
                baseTime = existingSubscription.getExpireAt();
            } else {
                // 否则从当前时间开始
                baseTime = new Date();
            }

            Date expireAt = calculateExpireTime(baseTime, plan);
            
            if (existingSubscription != null) {
                // 更新现有订阅
                existingSubscription.setPlanId(order.getProductId());
                existingSubscription.setPlanName(plan != null ? plan.getName() : null);
                existingSubscription.setExpireAt(expireAt);
                existingSubscription.setUpdatedAt(new Date());
                cloudSubscriptionRepository.updateById(existingSubscription);
                logger.info("已延长设备 {} 的云存储订阅至 {}", order.getDeviceId(), expireAt);
            } else {
                // 创建新的云存储订阅记录
                CloudSubscription subscription = new CloudSubscription();
                subscription.setUserId(order.getUserId());
                subscription.setDeviceId(order.getDeviceId());
                subscription.setPlanId(order.getProductId());
                subscription.setPlanName(plan != null ? plan.getName() : null);
                subscription.setExpireAt(expireAt);
                int defaultAutoRenew = (plan != null && plan.getAutoRenew() != null) ? plan.getAutoRenew() : 0;
                subscription.setAutoRenew(defaultAutoRenew);
                subscription.setCreatedAt(new Date());
                subscription.setUpdatedAt(new Date());
                cloudSubscriptionRepository.insert(subscription);
                logger.info("已创建设备 {} 的云存储订阅，到期时间 {}", order.getDeviceId(), expireAt);
            }

            // 更新设备云存储配置
            Device device = deviceRepository.selectById(order.getDeviceId());
            if (device != null) {
                device.setCloudStorage(1); // 启用云存储
                device.setUpdatedAt(LocalDateTime.now());
                deviceRepository.updateById(device);
                logger.info("已激活设备 {} 的云存储套餐", order.getDeviceId());
            }

            return true;
        } catch (Exception e) {
            logger.error("激活云存储套餐失败: orderId={}", order.getOrderId(), e);
            return false;
        }
    }

    /**
     * 根据套餐周期计算到期时间
     */
    private Date calculateExpireTime(Date baseTime, CloudPlan plan) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(baseTime);
        
        if (plan != null && plan.getPeriod() != null) {
            // 优先使用 periodNum（月数），兼容旧的 period 字符串
            if (plan.getPeriodNum() != null && plan.getPeriodNum() > 0) {
                cal.add(Calendar.MONTH, plan.getPeriodNum());
            } else if ("year".equals(plan.getPeriod())) {
                // 年付：加1年
                cal.add(Calendar.YEAR, 1);
            } else if ("month".equals(plan.getPeriod())) {
                // 月付：加1个月
                cal.add(Calendar.MONTH, 1);
            } else {
                // 默认：加30天
                cal.add(Calendar.DAY_OF_MONTH, 30);
            }
        } else {
            // 未找到套餐信息，默认加30天
            cal.add(Calendar.DAY_OF_MONTH, 30);
            logger.warn("未找到套餐信息，默认设置30天有效期");
        }
        
        return cal.getTime();
    }
}
