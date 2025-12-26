package com.pura365.camera.controller.app;

import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.model.payment.*;
import com.pura365.camera.service.GooglePayService;
import com.pura365.camera.service.PaymentService;
import com.pura365.camera.service.PaymentService.CreateOrderResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 支付接口
 * 
 * 提供订单创建、查询以及多种支付方式的对接：
 * - 微信支付 (WeChat Pay)
 * - PayPal
 * - Apple Pay
 */
@Tag(name = "支付管理", description = "订单创建、支付相关接口")
@RestController
@RequestMapping("/api/app/payment")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    @Autowired
    private PaymentService paymentService;

    /**
     * 创建支付订单
     * 
     * 根据商品类型和商品ID创建订单，返回订单号和金额信息
     */
    @Operation(summary = "创建支付订单", description = "根据商品类型创建订单，目前支持云存储套餐")
    @PostMapping("/create")
    public ApiResponse<OrderVO> createOrder(
            @RequestAttribute("currentUserId") Long currentUserId,
            @RequestBody CreateOrderRequest request) {
        log.info("创建支付订单 - userId={}, request={}", currentUserId, request);
        CreateOrderResult result = paymentService.createOrder(currentUserId, request);
        if (!result.isSuccess()) {
            log.warn("创建订单失败 - userId={}, error={}", currentUserId, result.getErrorMessage());
            return ApiResponse.error(result.getErrorCode(), result.getErrorMessage());
        }
        log.info("创建订单成功 - userId={}, orderId={}", currentUserId, result.getOrder().getOrderId());
        return ApiResponse.success(result.getOrder());
    }

    /**
     * 查询订单支付状态
     */
    @Operation(summary = "查询支付状态", description = "根据订单ID查询当前支付状态")
    @GetMapping("/{id}/status")
    public ApiResponse<OrderVO> getOrderStatus(
            @RequestAttribute("currentUserId") Long currentUserId,
            @Parameter(description = "订单ID") @PathVariable("id") String orderId) {
        log.info("查询订单状态 - userId={}, orderId={}", currentUserId, orderId);
        OrderVO order = paymentService.getOrderStatus(currentUserId, orderId);
        if (order == null) {
            log.warn("查询订单状态失败 - 订单不存在, userId={}, orderId={}", currentUserId, orderId);
            return ApiResponse.error(404, "订单不存在");
        }
        return ApiResponse.success(order);
    }

    /**
     * 获取微信支付参数
     * 
     * 返回客户端调起微信支付所需的全部参数
     */
    @Operation(summary = "微信支付", description = "获取微信支付所需参数，客户端使用返回参数调用微信SDK")
    @PostMapping("/wechat")
    public ApiResponse<WechatPayVO> wechatPay(
            @RequestAttribute("currentUserId") Long currentUserId,
            @RequestBody PayRequest request) {
        log.info("微信支付 - userId={}, orderId={}", currentUserId, request.getOrderId());
        if (!StringUtils.hasText(request.getOrderId())) {
            log.warn("微信支付失败 - orderId为空, userId={}", currentUserId);
            return ApiResponse.error(400, "order_id 不能为空");
        }
        WechatPayVO result = paymentService.wechatPay(currentUserId, request.getOrderId());
        if (result == null) {
            log.warn("微信支付失败 - 订单不存在, userId={}, orderId={}", currentUserId, request.getOrderId());
            return ApiResponse.error(404, "订单不存在");
        }
        log.info("微信支付参数获取成功 - userId={}, orderId={}", currentUserId, request.getOrderId());
        return ApiResponse.success(result);
    }

    /**
     * 获取 PayPal 支付链接
     * 
     * 返回 PayPal 支付页面 URL，客户端需跳转到该 URL 完成支付
     */
    @Operation(summary = "PayPal 支付", description = "获取 PayPal 支付页面 URL")
    @PostMapping("/paypal")
    public ApiResponse<PaypalPayVO> paypalPay(
            @RequestAttribute("currentUserId") Long currentUserId,
            @RequestBody PayRequest request) {
        log.info("PayPal支付 - userId={}, orderId={}", currentUserId, request.getOrderId());
        if (!StringUtils.hasText(request.getOrderId())) {
            log.warn("PayPal支付失败 - orderId为空, userId={}", currentUserId);
            return ApiResponse.error(400, "order_id 不能为空");
        }
        PaypalPayVO result = paymentService.paypalPay(currentUserId, request.getOrderId());
        if (result == null) {
            log.warn("PayPal支付失败 - 订单不存在, userId={}, orderId={}", currentUserId, request.getOrderId());
            return ApiResponse.error(404, "订单不存在");
        }
        log.info("PayPal支付URL获取成功 - userId={}, orderId={}", currentUserId, request.getOrderId());
        return ApiResponse.success(result);
    }

//    /**
//     * Apple Pay 支付
//     *
//     * 客户端传入 Apple Pay SDK 返回的 payment_token，服务端验证并完成支付
//     */
//    @Operation(summary = "Apple Pay 支付", description = "使用 Apple Pay 完成支付")
//    @PostMapping("/apple")
//    public ApiResponse<ApplePayVO> applePay(
//            @RequestAttribute("currentUserId") Long currentUserId,
//            @RequestBody ApplePayRequest request) {
//
//        if (!StringUtils.hasText(request.getOrderId())) {
//            return ApiResponse.error(400, "order_id 不能为空");
//        }
//
//        ApplePayVO result = paymentService.applePay(currentUserId, request);
//        if (result == null) {
//            return ApiResponse.error(404, "订单不存在");
//        }
//        return ApiResponse.success(result);
//    }
//
//    /**
//     * Google Play 支付
//     *
//     * 客户端传入Google Play Billing返回的purchase token，服务端验证并完成支付
//     */
//    @Operation(summary = "Google Play 支付", description = "使用 Google Play 完成支付")
//    @PostMapping("/google")
//    public ApiResponse<GooglePayVO> googlePay(
//            @RequestAttribute("currentUserId") Long currentUserId,
//            @RequestBody GooglePayRequest request) {
//
//        if (!StringUtils.hasText(request.getOrderId())) {
//            return ApiResponse.error(400, "order_id 不能为空");
//        }
//        if (!StringUtils.hasText(request.getPurchaseToken())) {
//            return ApiResponse.error(400, "purchase_token 不能为空");
//        }
//
//        GooglePayVO result = paymentService.googlePay(currentUserId, request);
//        if (result == null) {
//            return ApiResponse.error(404, "订单不存在");
//        }
//        return ApiResponse.success(result);
//    }
}
