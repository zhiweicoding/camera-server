package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pura365.camera.domain.PaymentOrder;
import com.pura365.camera.enums.PaymentOrderStatus;
import com.pura365.camera.model.payment.WechatPayVO;
import com.pura365.camera.repository.PaymentOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceWechatPayTest {

    @Test
    void wechatPay_delegatesToWechatPayService_forPendingOrder() {
        PaymentOrderRepository paymentOrderRepository = mock(PaymentOrderRepository.class);
        WechatPayService wechatPayService = mock(WechatPayService.class);

        PaymentService paymentService = new PaymentService();
        ReflectionTestUtils.setField(paymentService, "paymentOrderRepository", paymentOrderRepository);
        ReflectionTestUtils.setField(paymentService, "wechatPayService", wechatPayService);

        PaymentOrder order = new PaymentOrder();
        order.setOrderId("ORDER-001");
        order.setUserId(2L);
        order.setStatus(PaymentOrderStatus.PENDING);

        WechatPayVO expected = new WechatPayVO();
        expected.setAppid("wx-real-appid");
        expected.setPartnerid("1900000109");
        expected.setPrepayid("wx-prepay-id");

        when(paymentOrderRepository.selectOne(anyOrderQuery())).thenReturn(order);
        when(wechatPayService.createAppOrder(order)).thenReturn(expected);

        WechatPayVO actual = paymentService.wechatPay(2L, "ORDER-001");

        assertSame(expected, actual);
        verify(wechatPayService).createAppOrder(order);
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<PaymentOrder> anyOrderQuery() {
        return any(LambdaQueryWrapper.class);
    }
}
