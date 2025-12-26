package com.pura365.camera.service;

import com.paypal.api.payments.*;
import com.paypal.base.rest.APIContext;
import com.paypal.base.rest.PayPalRESTException;
import com.pura365.camera.config.PayPalConfig;
import com.pura365.camera.domain.PaymentOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * PayPal支付服务
 */
@Service
public class PayPalService {

    private static final Logger logger = LoggerFactory.getLogger(PayPalService.class);

    private final APIContext apiContext;
    private final PayPalConfig payPalConfig;

    public PayPalService(APIContext apiContext, PayPalConfig payPalConfig) {
        this.apiContext = apiContext;
        this.payPalConfig = payPalConfig;
    }

    /**
     * 创建PayPal支付
     *
     * @param order 订单信息
     * @return PayPal支付对象，包含approval_url
     */
    public Payment createPayment(PaymentOrder order) throws PayPalRESTException {
        // 设置金额
        Amount amount = new Amount();
        amount.setCurrency("USD");  // PayPal通常使用USD
        amount.setTotal(String.format("%.2f", order.getAmount()));

        // 设置交易信息
        Transaction transaction = new Transaction();
        transaction.setDescription("Cloud Storage Plan - " + order.getProductId());
        transaction.setAmount(amount);

        List<Transaction> transactions = new ArrayList<>();
        transactions.add(transaction);

        // 设置支付方式
        Payer payer = new Payer();
        payer.setPaymentMethod("paypal");

        // 设置支付对象
        Payment payment = new Payment();
        payment.setIntent("sale");
        payment.setPayer(payer);
        payment.setTransactions(transactions);

        // 设置回调URL
        RedirectUrls redirectUrls = new RedirectUrls();
        redirectUrls.setCancelUrl(payPalConfig.getCancelUrl() + "?order_id=" + order.getOrderId());
        redirectUrls.setReturnUrl(payPalConfig.getReturnUrl() + "?order_id=" + order.getOrderId());
        payment.setRedirectUrls(redirectUrls);

        // 创建支付
        Payment createdPayment = payment.create(apiContext);
        logger.info("PayPal支付创建成功: orderId={}, paymentId={}", order.getOrderId(), createdPayment.getId());

        return createdPayment;
    }

    /**
     * 执行PayPal支付
     *
     * @param paymentId PayPal支付ID
     * @param payerId   付款人ID
     * @return 执行后的支付对象
     */
    public Payment executePayment(String paymentId, String payerId) throws PayPalRESTException {
        Payment payment = new Payment();
        payment.setId(paymentId);

        PaymentExecution paymentExecution = new PaymentExecution();
        paymentExecution.setPayerId(payerId);

        Payment executedPayment = payment.execute(apiContext, paymentExecution);
        logger.info("PayPal支付执行成功: paymentId={}, state={}", paymentId, executedPayment.getState());

        return executedPayment;
    }

    /**
     * 获取PayPal支付详情
     *
     * @param paymentId PayPal支付ID
     * @return 支付对象
     */
    public Payment getPaymentDetails(String paymentId) throws PayPalRESTException {
        return Payment.get(apiContext, paymentId);
    }

    /**
     * 从Payment对象中提取approval_url
     *
     * @param payment PayPal支付对象
     * @return approval_url，用户需要访问此URL完成支付
     */
    public String getApprovalUrl(Payment payment) {
        for (Links link : payment.getLinks()) {
            if (link.getRel().equals("approval_url")) {
                return link.getHref();
            }
        }
        return null;
    }
}
