package com.pura365.camera.controller.admin;

import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.service.BillingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
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
     * 获取装机商账单汇总
     */
    @Operation(summary = "装机商账单汇总", description = "按装机商维度统计账单汇总")
    @GetMapping("/installer-summary")
    public ApiResponse<Map<String, Object>> getInstallerBillingSummary(
            @RequestParam(required = false) Long installerId,
            @RequestParam(required = false) String installerCode,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") String month) {
        // 支持按月查询
        if (month != null && !month.isEmpty()) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM");
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.setTime(sdf.parse(month));
                startDate = cal.getTime();
                cal.add(java.util.Calendar.MONTH, 1);
                cal.add(java.util.Calendar.DAY_OF_MONTH, -1);
                cal.set(java.util.Calendar.HOUR_OF_DAY, 23);
                cal.set(java.util.Calendar.MINUTE, 59);
                cal.set(java.util.Calendar.SECOND, 59);
                cal.set(java.util.Calendar.MILLISECOND, 999);
                endDate = cal.getTime();
            } catch (Exception e) {
                // 忽略解析错误
            }
        }
        return ApiResponse.success(billingService.getInstallerBillingSummary(installerId, installerCode, startDate, endDate));
    }

    /**
     * 获取经销商账单汇总
     */
    @Operation(summary = "经销商账单汇总", description = "按经销商维度统计账单汇总")
    @GetMapping("/dealer-summary")
    public ApiResponse<Map<String, Object>> getDealerBillingSummary(
            @RequestParam(required = false) String installerCode,
            @RequestParam(required = false) Long dealerId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {
        return ApiResponse.success(billingService.getDealerBillingSummary(installerCode, dealerId, startDate, endDate));
    }

    /**
     * 获取业务员账单汇总（已废弃）
     * @deprecated 使用 /dealer-summary 替代
     */
    @Deprecated
    @Operation(summary = "业务员账单汇总（已废弃）", description = "请使用经销商账单汇总接口 /dealer-summary")
    @GetMapping("/salesman-summary")
    public ApiResponse<Map<String, Object>> getSalesmanBillingSummary(
            @RequestParam(required = false) String vendorCode,
            @RequestParam(required = false) Long salesmanId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {
        // 向后兼容，调用新方法
        return ApiResponse.success(billingService.getDealerBillingSummary(vendorCode, salesmanId, startDate, endDate));
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

    /**
     * 导出充值明细Excel
     * 如果数据超过1万条，分成多个Excel文件并打包成zip
     */
    @Operation(summary = "导出充值明细Excel", description = "导出充值明细Excel文件，超过1万条数据分片打包成zip")
    @GetMapping("/export-detail")
    public ResponseEntity<byte[]> exportBillingDetail(
            @RequestParam(required = false) String vendorCode,
            @RequestParam(required = false) Long salesmanId,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {
        try {
            Map<String, Object> result = billingService.exportBillingDetailExcel(vendorCode, salesmanId, deviceId, startDate, endDate);
            boolean isZip = (Boolean) result.get("isZip");
            byte[] data = (byte[]) result.get("data");

            // 生成文件名
            String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            String filename = isZip ? "充值明细_" + timestamp + ".zip" : "充值明细_" + timestamp + ".xlsx";
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(isZip ? MediaType.valueOf("application/zip") : MediaType.APPLICATION_OCTET_STREAM);
            headers.set(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename);
            headers.setContentLength(data.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(data);

        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
