package com.pura365.camera.controller.admin;

import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.service.BillingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 账单统计接口
 */
@Tag(name = "账单统计", description = "账单统计和导出相关接口")
@RestController
@RequestMapping("/api/admin/billing")
public class BillingController {

    @Autowired
    private BillingService billingService;

    /**
     * 获取经销商账单汇总
     */
    @Operation(summary = "经销商账单汇总", description = "按经销商维度统计账单汇总")
    @GetMapping("/vendor-summary")
    public ApiResponse<Map<String, Object>> getVendorBillingSummary(
            @RequestParam(required = false) String vendorCode,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {
        return ApiResponse.success(billingService.getVendorBillingSummary(vendorCode, startDate, endDate));
    }

    /**
     * 获取业务员账单汇总
     */
    @Operation(summary = "业务员账单汇总", description = "按业务员维度统计账单汇总")
    @GetMapping("/salesman-summary")
    public ApiResponse<Map<String, Object>> getSalesmanBillingSummary(
            @RequestParam(required = false) String vendorCode,
            @RequestParam(required = false) Long salesmanId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {
        return ApiResponse.success(billingService.getSalesmanBillingSummary(vendorCode, salesmanId, startDate, endDate));
    }

    /**
     * 获取订单明细列表（用于导出）
     */
    @Operation(summary = "订单明细", description = "获取订单明细列表，用于导出")
    @GetMapping("/order-details")
    public ApiResponse<List<Map<String, Object>>> getOrderDetails(
            @RequestParam(required = false) String vendorCode,
            @RequestParam(required = false) Long salesmanId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {
        return ApiResponse.success(billingService.getOrderDetails(vendorCode, salesmanId, startDate, endDate));
    }

    /**
     * 分页查询订单列表
     */
    @Operation(summary = "分页查询订单", description = "分页查询订单列表，支持多条件筛选")
    @GetMapping("/orders")
    public ApiResponse<Map<String, Object>> listOrders(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String vendorCode,
            @RequestParam(required = false) Long salesmanId,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {
        return ApiResponse.success(billingService.listOrders(page, size, vendorCode, salesmanId, deviceId, status, startDate, endDate));
    }

    /**
     * 记录退款
     */
    @Operation(summary = "记录退款", description = "记录订单退款（只做记录）")
    @PostMapping("/refund")
    public ApiResponse<Void> recordRefund(@RequestBody Map<String, String> body) {
        try {
            String orderId = body.get("orderId");
            String reason = body.get("reason");
            if (orderId == null || orderId.trim().isEmpty()) {
                return ApiResponse.error(400, "订单ID不能为空");
            }
            billingService.recordRefund(orderId, reason);
            return ApiResponse.success(null);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 获取经销商下设备的支付统计
     */
    @Operation(summary = "设备支付统计", description = "获取经销商下各设备的支付统计")
    @GetMapping("/device-stats")
    public ApiResponse<Map<String, Object>> getDevicePaymentStats(
            @RequestParam String vendorCode,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {
        return ApiResponse.success(billingService.getDevicePaymentStats(vendorCode, startDate, endDate));
    }
}
