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
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentCallbackServiceAppleRepairTest {

    @Test
    void handlePaymentSuccess_repairsPaidAppleOrderMissingSubscription() {
        PaymentOrderRepository paymentOrderRepository = mock(PaymentOrderRepository.class);
        CloudSubscriptionRepository cloudSubscriptionRepository = mock(CloudSubscriptionRepository.class);
        DeviceRepository deviceRepository = mock(DeviceRepository.class);
        CloudPlanRepository cloudPlanRepository = mock(CloudPlanRepository.class);

        PaymentCallbackService service = new PaymentCallbackService(
                paymentOrderRepository,
                cloudSubscriptionRepository,
                deviceRepository,
                cloudPlanRepository
        );
        ReflectionTestUtils.setField(service, "commissionCalculateService", mock(CommissionCalculateService.class));

        PaymentOrder order = new PaymentOrder();
        order.setOrderId("ORDER-APPLE-001");
        order.setUserId(9L);
        order.setDeviceId("DEVICE-001");
        order.setProductType("motion");
        order.setProductId("motion-month");
        order.setStatus(PaymentOrderStatus.PAID);
        order.setPaymentMethod("apple");
        order.setPaidAt(new Date());
        order.setCreatedAt(new Date(order.getPaidAt().getTime() - 1000L));

        CloudPlan plan = new CloudPlan();
        plan.setPlanId("motion-month");
        plan.setName("Motion monthly");
        plan.setType("motion");
        plan.setPeriod("month");
        plan.setPeriodNum(1);

        Device device = new Device();
        device.setId("DEVICE-001");

        when(paymentOrderRepository.selectOne(anyOrderQuery())).thenReturn(order);
        when(cloudPlanRepository.selectOne(anyPlanQuery())).thenReturn(plan);
        when(cloudSubscriptionRepository.selectList(anySubscriptionQuery()))
                .thenReturn(Collections.emptyList());
        when(deviceRepository.selectById("DEVICE-001")).thenReturn(device);

        boolean success = service.handlePaymentSuccess("ORDER-APPLE-001", "apple", "tx-apple-001");

        assertTrue(success);
        verify(cloudSubscriptionRepository).insert(any(CloudSubscription.class));
        verify(deviceRepository).updateById(device);
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<PaymentOrder> anyOrderQuery() {
        return any(LambdaQueryWrapper.class);
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<CloudPlan> anyPlanQuery() {
        return any(LambdaQueryWrapper.class);
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<CloudSubscription> anySubscriptionQuery() {
        return any(LambdaQueryWrapper.class);
    }
}
