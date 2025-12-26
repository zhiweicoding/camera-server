package com.pura365.camera.controller.callback;

import com.github.binarywang.wxpay.bean.notify.WxPayNotifyResponse;
import com.github.binarywang.wxpay.bean.notify.WxPayOrderNotifyResult;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.github.binarywang.wxpay.service.WxPayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * 微信支付回调控制器
 */
@RestController
@RequestMapping("/api/callback")
public class WechatPayCallbackController {

    private static final Logger logger = LoggerFactory.getLogger(WechatPayCallbackController.class);

    private final WxPayService wxPayService;
    private final com.pura365.camera.service.PaymentCallbackService paymentCallbackService;

    public WechatPayCallbackController(WxPayService wxPayService,
                                        com.pura365.camera.service.PaymentCallbackService paymentCallbackService) {
        this.wxPayService = wxPayService;
        this.paymentCallbackService = paymentCallbackService;
    }

    /**
     * 微信支付回调接口
     */
    @PostMapping("/wechat")
    public String wechatPayNotify(@RequestBody String xmlData) {
        logger.info("收到微信支付回调通知");
        
        try {
            // 解析并验证签名
            WxPayOrderNotifyResult notifyResult = wxPayService.parseOrderNotifyResult(xmlData);
            
            logger.info("微信支付回调: orderId={}, totalFee={}, resultCode={}", 
                    notifyResult.getOutTradeNo(), 
                    notifyResult.getTotalFee(),
                    notifyResult.getResultCode());

            // 验证支付结果
            if ("SUCCESS".equals(notifyResult.getResultCode())) {
                String orderId = notifyResult.getOutTradeNo();
                String transactionId = notifyResult.getTransactionId();
                
                logger.info("微信支付成功: orderId={}, transactionId={}", orderId, transactionId);
                
                // 调用统一的支付回调处理服务
                paymentCallbackService.handlePaymentSuccess(orderId, "wechat", transactionId);
            } else {
                logger.warn("微信支付失败: orderId={}, errCode={}, errCodeDes={}", 
                        notifyResult.getOutTradeNo(),
                        notifyResult.getErrCode(),
                        notifyResult.getErrCodeDes());
            }

            // 返回成功响应给微信
            return WxPayNotifyResponse.success("成功");
            
        } catch (WxPayException e) {
            logger.error("处理微信支付回调失败", e);
            return WxPayNotifyResponse.fail("处理失败");
        }
    }
}
