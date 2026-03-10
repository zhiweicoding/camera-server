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

import com.pura365.camera.domain.User;
import com.pura365.camera.repository.UserRepository;

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

    @Autowired
    private UserRepository userRepository;

    /**
     * 获取装机商账单汇总
     * 权限说明：只能查看与当前用户关联的数据
     */
    @Operation(summary = "装机商账单汇总", description = "按装机商维度统计账单汇总")
    @GetMapping("/installer-summary")
    public ApiResponse<Map<String, Object>> getInstallerBillingSummary(
            @RequestAttribute(value = "currentUserId", required = false) Long currentUserId,
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
        return ApiResponse.success(billingService.getInstallerBillingSummary(currentUserId, installerId, installerCode, startDate, endDate));
    }

    /**
     * 获取经销商账单汇总
     * 权限说明：只能查看与当前用户关联的数据
     */
    @Operation(summary = "经销商账单汇总", description = "按经销商维度统计账单汇总")
    @GetMapping("/dealer-summary")
    public ApiResponse<Map<String, Object>> getDealerBillingSummary(
            @RequestAttribute(value = "currentUserId", required = false) Long currentUserId,
            @RequestParam(required = false) String installerCode,
            @RequestParam(required = false) Long dealerId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {
        return ApiResponse.success(billingService.getDealerBillingSummary(currentUserId, installerCode, dealerId, startDate, endDate));
    }

    /**
     * 获取订单明细列表（用于导出）
     */
    @Operation(summary = "订单明细", description = "获取订单明细列表，用于导出")
    @GetMapping("/order-details")
    public ApiResponse<List<Map<String, Object>>> getOrderDetails(
            @RequestAttribute(value = "currentUserId", required = false) Long currentUserId,
            @RequestParam(required = false) String installerCode,
            @RequestParam(required = false) Long dealerId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {
        return ApiResponse.success(billingService.getOrderDetails(currentUserId, installerCode, dealerId, startDate, endDate));
    }

    /**
     * 分页查询订单列表
     * 权限说明：只能查看与当前用户关联的订单
     */
    @Operation(summary = "分页查询订单", description = "分页查询订单列表，支持多条件筛选")
    @GetMapping("/orders")
    public ApiResponse<Map<String, Object>> listOrders(
            @RequestAttribute(value = "currentUserId", required = false) Long currentUserId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String dimension,
            @RequestParam(required = false) String installerCode,
            @RequestParam(required = false) Long dealerId,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {
        return ApiResponse.success(billingService.listOrders(currentUserId, page, size, dimension, installerCode, dealerId, deviceId, status, startDate, endDate));
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
     * 获取当前用户的账单汇总统计
     * 返回设备总数、经销商分润等汇总数据
     */
    @Operation(summary = "当前用户账单汇总", description = "获取当前用户的设备总数、经销商分润等汇总统计")
    @GetMapping("/my-summary")
    public ApiResponse<Map<String, Object>> getMySummary(
            @RequestAttribute(value = "currentUserId", required = false) Long currentUserId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {
        return ApiResponse.success(billingService.getMySummary(currentUserId, startDate, endDate));
    }

    /**
     * 获取装机商下设备的支付统计
     */
    @Operation(summary = "设备支付统计", description = "获取装机商下各设备的支付统计")
    @GetMapping("/device-stats")
    public ApiResponse<Map<String, Object>> getDevicePaymentStats(
            @RequestParam Long installerId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {
        return ApiResponse.success(billingService.getDevicePaymentStats(installerId, startDate, endDate));
    }

    /**
     * 导出充值明细Excel
     * 如果数据超过1万条，分成多个Excel文件并打包成zip
     * 权限说明：非管理员只能导出与自己关联的数据
     */
    @Operation(summary = "导出充值明细Excel", description = "导出充值明细Excel文件，超过1万条数据分片打包成zip")
    @GetMapping("/export-detail")
    public ResponseEntity<byte[]> exportBillingDetail(
            @RequestAttribute(value = "currentUserId", required = false) Long currentUserId,
            @RequestParam(required = false) String dimension,
            @RequestParam(required = false) String installerCode,
            @RequestParam(required = false) Long dealerId,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {
        try {
            Map<String, Object> result = billingService.exportBillingDetailExcel(currentUserId, dimension, installerCode, dealerId, deviceId, startDate, endDate);
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

    // ==================== 结算相关接口 ====================

    /**
     * 获取结算订单列表
     * 用于查看指定装机商/经销商的待结算/已结算订单
     */
    @Operation(summary = "结算订单列表", description = "获取指定装机商/经销商的结算订单列表")
    @GetMapping("/settlement-orders")
    public ApiResponse<Map<String, Object>> getSettlementOrders(
            @RequestParam(required = false) String installerCode,
            @RequestParam(required = false) Long dealerId,
            @RequestParam(required = false) Integer isSettled,
            @RequestParam(required = false) String dimension,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return ApiResponse.success(billingService.getSettlementOrders(installerCode, dealerId, isSettled, dimension, startDate, endDate, page, size));
    }

    /**
     * 批量结算订单
     * 权限：仅管理员(role=3)可操作
     */
    @Operation(summary = "批量结算", description = "批量结算订单，仅管理员可操作")
    @PostMapping("/settle")
    public ApiResponse<Map<String, Object>> settleOrders(
            @RequestAttribute(value = "currentUserId", required = false) Long currentUserId,
            @RequestBody Map<String, Object> body) {
        // 权限校验：仅管理员可操作
        if (currentUserId == null) {
            return ApiResponse.error(401, "未登录");
        }
        User user = userRepository.selectById(currentUserId);
        if (user == null || user.getRole() == null || user.getRole() != 3) {
            return ApiResponse.error(403, "无权限操作，仅管理员可进行结算");
        }

        @SuppressWarnings("unchecked")
        List<String> orderIds = (List<String>) body.get("orderIds");
        if (orderIds == null || orderIds.isEmpty()) {
            return ApiResponse.error(400, "订单ID列表不能为空");
        }
        String dimension = body.get("dimension") != null ? String.valueOf(body.get("dimension")) : null;
        if (dimension == null || (!"installer".equalsIgnoreCase(dimension) && !"dealer".equalsIgnoreCase(dimension))) {
            return ApiResponse.error(400, "Invalid settlement dimension, only installer/dealer supported");
        }

        int count = billingService.settleOrders(orderIds, currentUserId, dimension);
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("settledCount", count);
        result.put("message", "成功结算 " + count + " 笔订单");
        return ApiResponse.success(result);
    }

    /**
     * 导出结算表Excel
     * 权限说明：非管理员只能导出与自己关联的数据
     */
    @Operation(summary = "导出结算表", description = "导出指定装机商/经销商的结算表Excel")
    @GetMapping("/export-settlement")
    public ResponseEntity<byte[]> exportSettlement(
            @RequestAttribute(value = "currentUserId", required = false) Long currentUserId,
            @RequestParam(required = false) String installerCode,
            @RequestParam(required = false) Long dealerId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {
        try {
            Map<String, Object> result = billingService.exportSettlementExcel(currentUserId, installerCode, dealerId, startDate, endDate);
            byte[] data = (byte[]) result.get("data");

            // 生成文件名
            String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            String targetName = installerCode != null ? installerCode : (dealerId != null ? "经销商" + dealerId : "全部");
            String filename = "结算表_" + targetName + "_" + timestamp + ".xlsx";
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
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
