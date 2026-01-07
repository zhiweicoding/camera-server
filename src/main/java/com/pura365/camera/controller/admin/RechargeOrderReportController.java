package com.pura365.camera.controller.admin;

import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.model.report.PageResult;
import com.pura365.camera.model.report.RechargeOrderQueryRequest;
import com.pura365.camera.model.report.RechargeOrderReportVO;
import com.pura365.camera.service.RechargeOrderReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/**
 * 充值订单报表接口
 * 提供订单报表查询和导出功能
 */
@Tag(name = "充值订单报表", description = "充值订单报表查询和导出接口")
@RestController
@RequestMapping("/api/admin/recharge-report")
public class RechargeOrderReportController {

    private static final Logger log = LoggerFactory.getLogger(RechargeOrderReportController.class);

    @Autowired
    private RechargeOrderReportService reportService;

    /**
     * 分页查询充值订单报表
     */
    @Operation(summary = "分页查询充值订单报表", description = "分页查询充值订单报表，支持多条件筛选")
    @GetMapping
    public ApiResponse<PageResult<RechargeOrderReportVO>> queryOrders(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") Integer size,
            @Parameter(description = "充值订单号") @RequestParam(required = false) String orderId,
            @Parameter(description = "设备ID") @RequestParam(required = false) String deviceId,
            @Parameter(description = "经销商代码") @RequestParam(required = false) String vendorCode,
            @Parameter(description = "装机商代码") @RequestParam(required = false) String installerCode,
            @Parameter(description = "业务员ID") @RequestParam(required = false) Long salesmanId,
            @Parameter(description = "套餐ID") @RequestParam(required = false) String planId,
            @Parameter(description = "套餐类型") @RequestParam(required = false) String planType,
            @Parameter(description = "支付方式") @RequestParam(required = false) String paymentMethod,
            @Parameter(description = "支付币种") @RequestParam(required = false) String currency,
            @Parameter(description = "订单状态") @RequestParam(required = false) String status,
            @Parameter(description = "下单开始时间") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @Parameter(description = "下单结束时间") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate,
            @Parameter(description = "支付开始时间") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date payStartDate,
            @Parameter(description = "支付结束时间") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date payEndDate) {

        log.info("分页查询充值订单报表: page={}, size={}, vendorCode={}, salesmanId={}", 
                page, size, vendorCode, salesmanId);

        RechargeOrderQueryRequest request = new RechargeOrderQueryRequest();
        request.setPage(page);
        request.setSize(size);
        request.setOrderId(orderId);
        request.setDeviceId(deviceId);
        request.setVendorCode(vendorCode);
        request.setInstallerCode(installerCode);
        request.setSalesmanId(salesmanId);
        request.setPlanId(planId);
        request.setPlanType(planType);
        request.setPaymentMethod(paymentMethod);
        request.setCurrency(currency);
        request.setStatus(status);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setPayStartDate(payStartDate);
        request.setPayEndDate(payEndDate);

        PageResult<RechargeOrderReportVO> result = reportService.queryOrders(request);
        return ApiResponse.success(result);
    }

    /**
     * 通过POST请求分页查询（支持复杂条件）
     */
    @Operation(summary = "分页查询充值订单报表（POST）", description = "通过POST请求分页查询充值订单报表，支持复杂筛选条件")
    @PostMapping("/query")
    public ApiResponse<PageResult<RechargeOrderReportVO>> queryOrdersByPost(
            @RequestBody RechargeOrderQueryRequest request) {
        log.info("POST分页查询充值订单报表: page={}, size={}", request.getPage(), request.getSize());
        PageResult<RechargeOrderReportVO> result = reportService.queryOrders(request);
        return ApiResponse.success(result);
    }

    /**
     * 查询所有符合条件的订单（不分页）
     */
    @Operation(summary = "查询所有订单", description = "查询所有符合条件的订单，用于前端预览或统计")
    @PostMapping("/list")
    public ApiResponse<List<RechargeOrderReportVO>> queryAllOrders(
            @RequestBody RechargeOrderQueryRequest request) {
        log.info("查询所有充值订单");
        List<RechargeOrderReportVO> result = reportService.queryAllOrders(request);
        return ApiResponse.success(result);
    }

    /**
     * 导出充值订单报表Excel
     */
    @Operation(summary = "导出充值订单报表Excel", description = "根据筛选条件导出充值订单报表Excel文件")
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportExcel(
            @Parameter(description = "充值订单号") @RequestParam(required = false) String orderId,
            @Parameter(description = "设备ID") @RequestParam(required = false) String deviceId,
            @Parameter(description = "经销商代码") @RequestParam(required = false) String vendorCode,
            @Parameter(description = "装机商代码") @RequestParam(required = false) String installerCode,
            @Parameter(description = "业务员ID") @RequestParam(required = false) Long salesmanId,
            @Parameter(description = "套餐ID") @RequestParam(required = false) String planId,
            @Parameter(description = "套餐类型") @RequestParam(required = false) String planType,
            @Parameter(description = "支付方式") @RequestParam(required = false) String paymentMethod,
            @Parameter(description = "支付币种") @RequestParam(required = false) String currency,
            @Parameter(description = "订单状态") @RequestParam(required = false) String status,
            @Parameter(description = "下单开始时间") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @Parameter(description = "下单结束时间") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate,
            @Parameter(description = "支付开始时间") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date payStartDate,
            @Parameter(description = "支付结束时间") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date payEndDate) {

        log.info("导出充值订单报表Excel: vendorCode={}, installerCode={}, salesmanId={}", vendorCode, installerCode, salesmanId);

        RechargeOrderQueryRequest request = new RechargeOrderQueryRequest();
        request.setOrderId(orderId);
        request.setDeviceId(deviceId);
        request.setVendorCode(vendorCode);
        request.setInstallerCode(installerCode);
        request.setSalesmanId(salesmanId);
        request.setPlanId(planId);
        request.setPlanType(planType);
        request.setPaymentMethod(paymentMethod);
        request.setCurrency(currency);
        request.setStatus(status);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setPayStartDate(payStartDate);
        request.setPayEndDate(payEndDate);

        try {
            byte[] excelData = reportService.exportToExcel(request);

            // 生成文件名
            String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            String filename = "充值订单报表_" + timestamp + ".xlsx";
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.set(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename);
            headers.setContentLength(excelData.length);

            log.info("导出充值订单报表Excel成功: filename={}", filename);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(excelData);

        } catch (IOException e) {
            log.error("导出充值订单报表Excel失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 通过POST请求导出（支持复杂条件）
     */
    @Operation(summary = "导出充值订单报表Excel（POST）", description = "通过POST请求导出充值订单报表Excel文件，支持复杂筛选条件")
    @PostMapping("/export")
    public ResponseEntity<byte[]> exportExcelByPost(
            @RequestBody RechargeOrderQueryRequest request) {

        log.info("POST导出充值订单报表Excel");

        try {
            byte[] excelData = reportService.exportToExcel(request);

            // 生成文件名
            String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            String filename = "充值订单报表_" + timestamp + ".xlsx";
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.set(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename);
            headers.setContentLength(excelData.length);

            log.info("POST导出充值订单报表Excel成功: filename={}", filename);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(excelData);

        } catch (IOException e) {
            log.error("POST导出充值订单报表Excel失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
