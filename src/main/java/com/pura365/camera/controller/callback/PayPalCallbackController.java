package com.pura365.camera.controller.callback;

import com.paypal.api.payments.Payment;
import com.paypal.base.rest.PayPalRESTException;
import com.pura365.camera.service.PayPalService;
import com.pura365.camera.service.PaymentCallbackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

/**
 * PayPal支付回调控制器
 */
@RestController
@RequestMapping("/api/callback/paypal")
public class PayPalCallbackController {

    private static final Logger logger = LoggerFactory.getLogger(PayPalCallbackController.class);

    private final PayPalService payPalService;
    private final PaymentCallbackService paymentCallbackService;

    public PayPalCallbackController(PayPalService payPalService,
                                     PaymentCallbackService paymentCallbackService) {
        this.payPalService = payPalService;
        this.paymentCallbackService = paymentCallbackService;
    }

    /**
     * PayPal支付成功回调
     * 
     * PayPal会重定向到这个URL，带上paymentId和PayerID参数
     */
    @GetMapping("/success")
    public RedirectView paymentSuccess(
            @RequestParam("paymentId") String paymentId,
            @RequestParam("PayerID") String payerId,
            @RequestParam("order_id") String orderId) {
        
        logger.info("收到PayPal支付成功回调: orderId={}, paymentId={}, payerId={}", 
                orderId, paymentId, payerId);

        try {
            // 执行PayPal支付
            Payment payment = payPalService.executePayment(paymentId, payerId);

            if ("approved".equals(payment.getState())) {
                logger.info("PayPal支付验证成功: orderId={}, paymentId={}", orderId, paymentId);
                
                // 调用统一的支付回调处理服务
                paymentCallbackService.handlePaymentSuccess(orderId, "paypal", paymentId);
                
                // 重定向到成功页面（这里应该是你的APP的deep link或web页面）
                return new RedirectView("https://cam.pura365.cn/payment/success?order_id=" + orderId);
            } else {
                logger.warn("PayPal支付状态异常: orderId={}, state={}", orderId, payment.getState());
                return new RedirectView("https://cam.pura365.cn/payment/failed?order_id=" + orderId);
            }

        } catch (PayPalRESTException e) {
            logger.error("执行PayPal支付失败: orderId={}, paymentId={}", orderId, paymentId, e);
            return new RedirectView("https://cam.pura365.cn/payment/failed?order_id=" + orderId);
        }
    }

    /**
     * PayPal支付取消回调
     * 
     * 用户在PayPal页面点击取消会跳转到这里
     */
    @GetMapping("/cancel")
    public RedirectView paymentCancel(@RequestParam("order_id") String orderId) {
        logger.info("PayPal支付被取消: orderId={}", orderId);
        
        // 重定向到取消页面
        return new RedirectView("https://cam.pura365.cn/payment/cancelled?order_id=" + orderId);
    }
}
