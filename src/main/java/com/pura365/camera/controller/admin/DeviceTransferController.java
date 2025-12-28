package com.pura365.camera.controller.admin;

import com.pura365.camera.domain.DeviceTransfer;
import com.pura365.camera.domain.DeviceVendor;
import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.service.DeviceTransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 设备转让管理接口
 */
@Tag(name = "设备转让管理", description = "设备转让和分配相关接口")
@RestController
@RequestMapping("/api/admin/device-transfers")
public class DeviceTransferController {

    @Autowired
    private DeviceTransferService transferService;

    /**
     * 创建设备转让（经销商之间）
     * 请求体:
     * {
     *   "fromVendorId": 1,         // 转出经销商ID
     *   "toVendorId": 2,           // 转入经销商ID
     *   "deviceIds": ["A110000000000001", "A110000000000002"],
     *   "commissionRate": 50.00,   // 分润比例
     *   "remark": "批量转让"
     * }
     */
    @Operation(summary = "创建设备转让", description = "经销商将设备转让给另一个经销商")
    @PostMapping
    public ApiResponse<DeviceTransfer> createTransfer(@RequestBody Map<String, Object> body) {
        try {
            Long fromVendorId = body.get("fromVendorId") != null ? 
                    Long.valueOf(body.get("fromVendorId").toString()) : null;
            Long toVendorId = Long.valueOf(body.get("toVendorId").toString());
            @SuppressWarnings("unchecked")
            List<String> deviceIds = (List<String>) body.get("deviceIds");
            BigDecimal commissionRate = new BigDecimal(body.get("commissionRate").toString());
            String remark = (String) body.get("remark");

            DeviceTransfer transfer = transferService.createTransfer(
                    fromVendorId, toVendorId, deviceIds, commissionRate, remark);
            return ApiResponse.success(transfer);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 装机商分配设备给经销商
     * 请求体:
     * {
     *   "installerId": 1,          // 装机商ID
     *   "vendorId": 2,             // 经销商ID
     *   "deviceIds": ["A110000000000001", "A110000000000002"],
     *   "commissionRate": 50.00,   // 分润比例
     *   "remark": "初始分配"
     * }
     */
    @Operation(summary = "装机商分配设备", description = "装机商将设备分配给一级经销商")
    @PostMapping("/assign")
    public ApiResponse<DeviceTransfer> assignToVendor(@RequestBody Map<String, Object> body) {
        try {
            Long installerId = Long.valueOf(body.get("installerId").toString());
            Long vendorId = Long.valueOf(body.get("vendorId").toString());
            @SuppressWarnings("unchecked")
            List<String> deviceIds = (List<String>) body.get("deviceIds");
            BigDecimal commissionRate = new BigDecimal(body.get("commissionRate").toString());
            String remark = (String) body.get("remark");

            DeviceTransfer transfer = transferService.assignToVendor(
                    installerId, vendorId, deviceIds, commissionRate, remark);
            return ApiResponse.success(transfer);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 分页查询转让记录
     */
    @Operation(summary = "查询转让记录", description = "分页查询设备转让记录")
    @GetMapping
    public ApiResponse<Map<String, Object>> listTransfers(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") Integer size,
            @Parameter(description = "经销商ID") @RequestParam(required = false) Long vendorId,
            @Parameter(description = "装机商ID") @RequestParam(required = false) Long installerId,
            @Parameter(description = "状态") @RequestParam(required = false) String status) {
        return ApiResponse.success(transferService.listTransfers(page, size, vendorId, installerId, status));
    }

    /**
     * 获取转让详情
     */
    @Operation(summary = "获取转让详情", description = "根据ID获取转让记录详情")
    @GetMapping("/{id}")
    public ApiResponse<DeviceTransfer> getTransfer(@PathVariable Long id) {
        DeviceTransfer transfer = transferService.getTransferById(id);
        if (transfer == null) {
            return ApiResponse.error(404, "转让记录不存在");
        }
        return ApiResponse.success(transfer);
    }

    /**
     * 根据转让单号获取转让记录
     */
    @Operation(summary = "根据单号获取转让", description = "根据转让单号获取转让记录")
    @GetMapping("/no/{transferNo}")
    public ApiResponse<DeviceTransfer> getByTransferNo(@PathVariable String transferNo) {
        DeviceTransfer transfer = transferService.getByTransferNo(transferNo);
        if (transfer == null) {
            return ApiResponse.error(404, "转让记录不存在");
        }
        return ApiResponse.success(transfer);
    }

    /**
     * 获取经销商的设备列表
     */
    @Operation(summary = "获取经销商设备", description = "获取指定经销商名下的设备列表")
    @GetMapping("/vendor/{vendorId}/devices")
    public ApiResponse<List<DeviceVendor>> listDevicesByVendor(@PathVariable Long vendorId) {
        return ApiResponse.success(transferService.listDevicesByVendor(vendorId));
    }

    /**
     * 获取设备的分销链路
     */
    @Operation(summary = "获取设备分销链路", description = "获取设备的完整经销商分销链路")
    @GetMapping("/device/{deviceId}/chain")
    public ApiResponse<List<DeviceVendor>> getDeviceVendorChain(@PathVariable String deviceId) {
        return ApiResponse.success(transferService.getDeviceVendorChain(deviceId));
    }
}
