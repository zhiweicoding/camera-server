package com.pura365.camera.service;

import com.github.binarywang.wxpay.bean.order.WxPayAppOrderResult;
import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderRequest;
import com.github.binarywang.wxpay.constant.WxPayConstants;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.github.binarywang.wxpay.service.WxPayService;
import com.pura365.camera.config.WechatPayConfig;
import com.pura365.camera.domain.PaymentOrder;
import com.pura365.camera.model.payment.WechatPayVO;
import com.pura365.camera.repository.PaymentOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 微信支付服务
 */
@Service
public class WechatPayService {

    private static final Logger logger = LoggerFactory.getLogger(WechatPayService.class);

    private final WxPayService wxPayService;
    private final WechatPayConfig wechatPayConfig;
    private final PaymentOrderRepository paymentOrderRepository;

    public WechatPayService(WxPayService wxPayService,
                            WechatPayConfig wechatPayConfig,
                            PaymentOrderRepository paymentOrderRepository) {
        this.wxPayService = wxPayService;
        this.wechatPayConfig = wechatPayConfig;
        this.paymentOrderRepository = paymentOrderRepository;
    }

    /**
     * 创建微信APP支付订单
     *
     * @param order 支付订单
     * @return 微信支付参数
     */
    public WechatPayVO createAppOrder(PaymentOrder order) {
        // 开发/测试环境: 直接使用mock模式
        // 如果配置了use-sandbox=true或者配置项未填写,直接返回mock数据
        if (wechatPayConfig.getUseSandbox() || "YOUR_MCH_ID".equals(wechatPayConfig.getMchId())) {
            logger.info("使用Mock模式创建微信支付订单: orderId={}", order.getOrderId());
            return createMockWechatPayVO(order);
        }
        
        try {
            // 构建统一下单请求
            WxPayUnifiedOrderRequest request = new WxPayUnifiedOrderRequest();
            request.setAppid(wechatPayConfig.getAppId());
            request.setMchId(wechatPayConfig.getMchId());
            request.setBody("云存储套餐");
            request.setOutTradeNo(order.getOrderId());
            // 金额单位:分
            request.setTotalFee(order.getAmount().multiply(new java.math.BigDecimal("100")).intValue());
            request.setSpbillCreateIp("127.0.0.1");
            request.setNotifyUrl(wechatPayConfig.getNotifyUrl());
            request.setTradeType(WxPayConstants.TradeType.APP);

            logger.info("创建微信支付订单: orderId={}, amount={}", order.getOrderId(), order.getAmount());

            // 调用微信统一下单API
            WxPayAppOrderResult result = wxPayService.createOrder(request);

            // 构建返回结果
            WechatPayVO vo = new WechatPayVO();
            vo.setAppid(result.getAppId());
            vo.setPartnerid(result.getPartnerId());
            vo.setPrepayid(result.getPrepayId());
            vo.setPackageValue(result.getPackageValue());
            vo.setNoncestr(result.getNonceStr());
            vo.setTimestamp(result.getTimeStamp());
            vo.setSign(result.getSign());

            logger.info("微信支付订单创建成功: prepayId={}", result.getPrepayId());
            return vo;
        } catch (WxPayException e) {
            logger.error("创建微信支付订单失败: orderId={}, errorCode={}, errorMsg={}", 
                    order.getOrderId(), e.getErrCode(), e.getErrCodeDes(), e);
            
            // 沙盒环境可能会失败,返回mock数据用于测试
            if (wechatPayConfig.getUseSandbox()) {
                logger.warn("沙盒环境支付失败,返回mock数据");
                return createMockWechatPayVO(order);
            }
            
            throw new RuntimeException("创建微信支付订单失败: " + e.getErrCodeDes());
        }
    }

    /**
     * 创建Mock微信支付参数(用于沙盒测试)
     */
    private WechatPayVO createMockWechatPayVO(PaymentOrder order) {
        WechatPayVO vo = new WechatPayVO();
        vo.setAppid(wechatPayConfig.getAppId());
        vo.setPartnerid(wechatPayConfig.getMchId());
        vo.setPrepayid("sandbox_prepay_" + order.getOrderId());
        vo.setPackageValue("Sign=WXPay");
        vo.setNoncestr(java.util.UUID.randomUUID().toString().replace("-", ""));
        vo.setTimestamp(String.valueOf(System.currentTimeMillis() / 1000));
        vo.setSign("sandbox_sign_" + order.getOrderId());
        return vo;
    }
}
