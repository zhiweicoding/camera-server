package com.pura365.camera.controller.callback;

import com.github.binarywang.wxpay.bean.notify.WxPayNotifyResponse;
import com.github.binarywang.wxpay.bean.notify.WxPayOrderNotifyResult;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.github.binarywang.wxpay.service.WxPayService;
import com.pura365.camera.config.WechatPayConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 微信支付回调控制器
 */
@Tag(name = "微信支付回调", description = "微信支付回调接口（无需登录）")
@RestController
@RequestMapping("/api/callback")
public class WechatPayCallbackController {

    private static final Logger logger = LoggerFactory.getLogger(WechatPayCallbackController.class);

    private final WxPayService wxPayService;
    private final WechatPayConfig wechatPayConfig;
    private final com.pura365.camera.service.PaymentCallbackService paymentCallbackService;

    public WechatPayCallbackController(WxPayService wxPayService,
                                        WechatPayConfig wechatPayConfig,
                                        com.pura365.camera.service.PaymentCallbackService paymentCallbackService) {
        this.wxPayService = wxPayService;
        this.wechatPayConfig = wechatPayConfig;
        this.paymentCallbackService = paymentCallbackService;
    }

    /**
     * 微信支付回调接口
     */
    @Operation(summary = "微信支付回调", description = "接收微信服务器推送的支付通知")
    @PostMapping("/wechat")
    public String wechatPayNotify(@RequestBody String xmlData) {
        logger.info("收到微信支付回调通知: {}", xmlData);
        
        try {
            String orderId;
            String transactionId;
            String resultCode;

            // 判断是否跳过验证（开发测试模式）
            if (wechatPayConfig.getSkipVerify()) {
                logger.warn("开发模式: 跳过微信支付回调签名验证");
                // 直接从XML解析关键字段
                orderId = extractXmlValue(xmlData, "out_trade_no");
                transactionId = extractXmlValue(xmlData, "transaction_id");
                resultCode = extractXmlValue(xmlData, "result_code");
                
                // 如果result_code为空，默认为成功（方便测试）
                if (resultCode == null || resultCode.isEmpty()) {
                    resultCode = "SUCCESS";
                }
                // 如果transaction_id为空，生成一个模拟的
                if (transactionId == null || transactionId.isEmpty()) {
                    transactionId = "DEV_" + System.currentTimeMillis();
                }
            } else {
                // 正式模式: 解析并验证签名
                WxPayOrderNotifyResult notifyResult = wxPayService.parseOrderNotifyResult(xmlData);
                orderId = notifyResult.getOutTradeNo();
                transactionId = notifyResult.getTransactionId();
                resultCode = notifyResult.getResultCode();
            }
            
            logger.info("微信支付回调: orderId={}, transactionId={}, resultCode={}", 
                    orderId, transactionId, resultCode);

            // 验证支付结果
            if ("SUCCESS".equals(resultCode)) {
                logger.info("微信支付成功: orderId={}, transactionId={}", orderId, transactionId);
                // 调用统一的支付回调处理服务
                paymentCallbackService.handlePaymentSuccess(orderId, "wechat", transactionId);
            } else {
                logger.warn("微信支付失败: orderId={}, resultCode={}", orderId, resultCode);
            }

            // 返回成功响应给微信
            return WxPayNotifyResponse.success("成功");
            
        } catch (WxPayException e) {
            logger.error("处理微信支付回调失败", e);
            return WxPayNotifyResponse.fail("处理失败");
        }
    }

    /**
     * 从XML中提取指定标签的值
     */
    private String extractXmlValue(String xml, String tagName) {
        Pattern pattern = Pattern.compile("<" + tagName + "><!\\[CDATA\\[(.+?)\\]\\]></" + tagName + ">");
        Matcher matcher = pattern.matcher(xml);
        if (matcher.find()) {
            return matcher.group(1);
        }
        // 尝试不带CDATA的格式
        pattern = Pattern.compile("<" + tagName + ">(.+?)</" + tagName + ">");
        matcher = pattern.matcher(xml);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
