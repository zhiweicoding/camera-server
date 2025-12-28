package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pura365.camera.domain.CloudSubscription;
import com.pura365.camera.domain.Device;
import com.pura365.camera.domain.PaymentOrder;
import com.pura365.camera.enums.PaymentOrderStatus;
import com.pura365.camera.repository.CloudSubscriptionRepository;
import com.pura365.camera.repository.DeviceRepository;
import com.pura365.camera.repository.PaymentOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * 支付回调统一处理服务
 * 
 * 处理支付成功后的业务逻辑,包括:
 * - 更新订单状态
 * - 激活云存储套餐
 * - 更新设备配置
 */
@Service
public class PaymentCallbackService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentCallbackService.class);

    private final PaymentOrderRepository paymentOrderRepository;
    private final CloudSubscriptionRepository cloudSubscriptionRepository;
    private final DeviceRepository deviceRepository;

    public PaymentCallbackService(PaymentOrderRepository paymentOrderRepository,
                                   CloudSubscriptionRepository cloudSubscriptionRepository,
                                   DeviceRepository deviceRepository) {
        this.paymentOrderRepository = paymentOrderRepository;
        this.cloudSubscriptionRepository = cloudSubscriptionRepository;
        this.deviceRepository = deviceRepository;
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
            // 创建云存储订阅记录
            CloudSubscription subscription = new CloudSubscription();
            subscription.setUserId(order.getUserId());
            subscription.setDeviceId(order.getDeviceId());
            subscription.setPlanId(order.getProductId());
            // TODO: 根据套餐类型计算过期时间，这里默认7天
            Date expireAt = new Date(System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L);
            subscription.setExpireAt(expireAt);
            subscription.setAutoRenew(0); // 默认不自动续费
            subscription.setCreatedAt(new Date());
            subscription.setUpdatedAt(new Date());
            
            cloudSubscriptionRepository.insert(subscription);

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
}
