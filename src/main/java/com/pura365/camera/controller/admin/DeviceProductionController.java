package com.pura365.camera.controller.admin;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.pura365.camera.domain.DeviceProductionBatch;
import com.pura365.camera.domain.ManufacturedDevice;
import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.model.CreateBatchRequest;
import com.pura365.camera.service.DeviceProductionService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备生产管理接口
 * 包括：装机商管理、生产批次管理、设备管理
 */
@Tag(name = "设备生产管理", description = "装机商管理、生产批次管理、设备管理相关接口")
@RestController
@RequestMapping("/api/admin/device-production")
public class DeviceProductionController {

    @Autowired
    private DeviceProductionService productionService;

    // ==================== 装机商管理接口 ====================

    /**
     * 获取启用的装机商列表
     */
    @GetMapping("/assemblers")
    public ApiResponse<List<?>> listAssemblers() {
        return ApiResponse.success(productionService.listAssemblers());
    }

    // ==================== 配置选项接口 ====================

    /**
     * 获取设备ID生成的所有配置选项
     * 返回：network_lens, device_form, special_req, reserved, assemblers, vendors
     */
    @GetMapping("/options")
    public ApiResponse<Map<String, Object>> getOptions() {
        return ApiResponse.success(productionService.getOptions());
    }

    // ==================== 生产批次管理接口 ====================

    /**
     * 创建生产批次并生成设备ID
     * 请求体：{
     *   "networkLens": "A1",       // 网络+镜头配置 (第1-2位)
     *   "deviceForm": "1",         // 设备形态 (第3位)
     *   "specialReq": "0",         // 特殊要求 (第4位)
     *   "assemblerCode": "0",      // 装机商代码 (第5位)
     *   "vendorCode": "00",        // 经销商代码 (第6-7位)
     *   "reserved": "0",           // 预留位 (第8位)，可选，默认"0"
     *   "quantity": 100,           // 生产数量
     *   "startSerial": 1,          // 起始序列号，可选，不填则自动识别
     *   "remark": "备注",          // 备注，可选
     *   "createdBy": "admin"       // 创建人，可选
     * }
     */
    @PostMapping("/batch")
    public ApiResponse<Map<String, Object>> createBatch(@RequestBody CreateBatchRequest request) {
        try {
            DeviceProductionBatch batch = productionService.createBatch(request);

            Map<String, Object> resp = new HashMap<>();
            resp.put("batch_no", batch.getBatchNo());
            resp.put("start_serial", batch.getStartSerial());
            resp.put("end_serial", batch.getEndSerial());
            resp.put("quantity", batch.getQuantity());
            resp.put("device_id_prefix", batch.getDeviceIdPrefix());
            return ApiResponse.success(resp);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "服务器错误");
        }
    }

    /**
     * 获取批次详情
     */
    @GetMapping("/batch/{batchNo}")
    public ApiResponse<DeviceProductionBatch> getBatch(@PathVariable String batchNo) {
        DeviceProductionBatch batch = productionService.getBatchByNo(batchNo);
        if (batch == null) {
            return ApiResponse.error(404, "批次不存在");
        }
        return ApiResponse.success(batch);
    }

    /**
     * 获取批次下的所有设备列表
     */
    @GetMapping("/batch/{batchNo}/devices")
    public ApiResponse<List<ManufacturedDevice>> listDevicesInBatch(@PathVariable String batchNo) {
        return ApiResponse.success(productionService.listDevicesByBatch(batchNo));
    }

    /**
     * 导出批次机身号为TXT文件
     * 每行一个机身号
     */
    @GetMapping("/batch/{batchNo}/export-txt")
    public ResponseEntity<byte[]> exportBatchDevicesTxt(@PathVariable String batchNo) {
        try {
            List<ManufacturedDevice> devices = productionService.listDevicesByBatch(batchNo);
            if (devices == null || devices.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            // 生成TXT内容，每行一个机身号
            StringBuilder sb = new StringBuilder();
            for (ManufacturedDevice device : devices) {
                sb.append(device.getDeviceId()).append("\n");
            }

            byte[] data = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);

            // 设置响应头
            String filename = "batch_" + batchNo + "_devices.txt";
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setContentType(MediaType.TEXT_PLAIN);
            responseHeaders.setContentDispositionFormData("attachment", 
                new String(filename.getBytes("UTF-8"), "ISO-8859-1"));
            responseHeaders.setContentLength(data.length);

            return ResponseEntity.ok()
                    .headers(responseHeaders)
                    .body(data);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // ==================== 设备管理接口 ====================

    /**
     * 分页查询设备列表
     * 参数：
     * - page: 页码，从1开始
     * - size: 每页大小
     * - deviceId: 设备ID（模糊查询，可选）
     * - batchNo: 批次号（可选）
     * - status: 状态（可选）
     * - installerCode: 装机商代码（可选）
     * - dealerCode: 经销商代码（可选）
     * 
     * 权限说明：
     * - 管理员(role=3)：可查看所有设备
     * - 装机商(isInstaller=1)：只能查看自己关联的设备
     * - 经销商(isDealer=1)：只能查看自己关联的设备
     */
    @GetMapping("/devices")
    public ApiResponse<Map<String, Object>> listDevices(
            @RequestAttribute(value = "currentUserId", required = false) Long currentUserId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String installerCode,
            @RequestParam(required = false) String dealerCode) {
        Map<String, Object> result = productionService.listDevices(currentUserId, page, size, deviceId, batchNo, status, installerCode, dealerCode);
        return ApiResponse.success(result);
    }

    /**
     * 获取单个设备详情（包含分销链路等丰富信息）
     */
    @GetMapping("/devices/{deviceId}")
    public ApiResponse<Map<String, Object>> getDevice(
            @RequestAttribute(value = "currentUserId", required = false) Long currentUserId,
            @PathVariable String deviceId) {
        Map<String, Object> device = productionService.getDeviceDetail(currentUserId, deviceId);
        if (device == null) {
            return ApiResponse.error(404, "设备不存在");
        }
        return ApiResponse.success(device);
    }

    /**
     * 创建单个设备
     * 请求体：{
     *   "deviceId": "A110000000000001",
     *   "batchId": 1,
     *   "networkLens": "A1",
     *   "deviceForm": "1",
     *   "specialReq": "0",
     *   "assemblerCode": "0",
     *   "vendorCode": "00",
     *   "serialNo": "00000001",
     *   "macAddress": "00:11:22:33:44:55",
     *   "status": "manufactured"
     * }
     */
    @PostMapping("/devices")
    public ApiResponse<ManufacturedDevice> createDevice(@RequestBody ManufacturedDevice device) {
        try {
            ManufacturedDevice created = productionService.createDevice(device);
            return ApiResponse.success(created);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 更新设备信息
     */
    @PutMapping("/devices/{id}")
    public ApiResponse<ManufacturedDevice> updateDevice(
            @PathVariable Long id,
            @RequestBody ManufacturedDevice device) {
        try {
            ManufacturedDevice updated = productionService.updateDevice(id, device);
            return ApiResponse.success(updated);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 批量删除设备
     * 请求体：{ "ids": [1, 2, 3] }
     */
    @DeleteMapping("/devices")
    public ApiResponse<Void> deleteDevices(@RequestBody Map<String, List<Long>> body) {
        try {
            List<Long> ids = body.get("ids");
            productionService.deleteDevices(ids);
            return ApiResponse.success(null);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 导出设备列表为Excel
     * 参数：
     * - deviceId: 设备ID（模糊查询，可选）
     * - batchNo: 批次号（可选）
     * - status: 状态（可选）
     */
    @GetMapping("/devices/export")
    public ResponseEntity<byte[]> exportDevices(
            @RequestAttribute(value = "currentUserId", required = false) Long currentUserId,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String status) {
        try {
            List<ManufacturedDevice> devices = productionService.listAllDevices(currentUserId, deviceId, batchNo, status);
            
            // 创建Excel工作簿
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("设备列表");
            
            // 创建标题行样式
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            
            // 创建标题行
            Row headerRow = sheet.createRow(0);
            String[] headers = {"ID", "设备ID", "批次ID", "网络+镜头", "设备形态", "特殊要求", 
                               "装机商代码", "经销商代码", "序列号", "MAC地址", "状态", 
                               "生产时间", "激活时间", "创建时间"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // 填充数据
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            int rowNum = 1;
            for (ManufacturedDevice device : devices) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(device.getId());
                row.createCell(1).setCellValue(device.getDeviceId());
                row.createCell(2).setCellValue(device.getBatchId());
                row.createCell(3).setCellValue(device.getNetworkLens());
                row.createCell(4).setCellValue(device.getDeviceForm());
                row.createCell(5).setCellValue(device.getSpecialReq());
                row.createCell(6).setCellValue(device.getAssemblerCode());
                row.createCell(7).setCellValue(device.getVendorCode());
                row.createCell(8).setCellValue(device.getSerialNo());
                row.createCell(9).setCellValue(device.getMacAddress());
                row.createCell(10).setCellValue(device.getStatus() != null ? device.getStatus().getCode() : "");
                row.createCell(11).setCellValue(device.getManufacturedAt() != null ? 
                    sdf.format(device.getManufacturedAt()) : "");
                row.createCell(12).setCellValue(device.getActivatedAt() != null ? 
                    sdf.format(device.getActivatedAt()) : "");
                row.createCell(13).setCellValue(device.getCreatedAt() != null ? 
                    sdf.format(device.getCreatedAt()) : "");
            }
            
            // 自动调整列宽
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            // 输出到字节数组
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            workbook.close();
            
            // 设置响应头
            String filename = "devices_" + new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date()) + ".xlsx";
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            responseHeaders.setContentDispositionFormData("attachment", new String(filename.getBytes("UTF-8"), "ISO-8859-1"));
            
            return ResponseEntity.ok()
                    .headers(responseHeaders)
                    .body(outputStream.toByteArray());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // ==================== 业务员分配接口 ====================

    /**
     * 分配业务员到设备
     * 请求体：{ "salesmanId": 1 }
     */
    @PutMapping("/devices/{deviceId}/salesman")
    public ApiResponse<Void> assignSalesman(
            @PathVariable String deviceId,
            @RequestBody Map<String, Long> body) {
        try {
            Long salesmanId = body.get("salesmanId");
            if (salesmanId == null) {
                return ApiResponse.error(400, "业务员ID不能为空");
            }
            productionService.assignSalesman(deviceId, salesmanId);
            return ApiResponse.success(null);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 批量分配业务员到设备
     * 请求体：{ "deviceIds": ["A110000000000001", "A110000000000002"], "salesmanId": 1 }
     */
    @PutMapping("/devices/batch-assign-salesman")
    public ApiResponse<Void> batchAssignSalesman(@RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<String> deviceIds = (List<String>) body.get("deviceIds");
            Long salesmanId = Long.valueOf(body.get("salesmanId").toString());
            if (deviceIds == null || deviceIds.isEmpty()) {
                return ApiResponse.error(400, "设备ID列表不能为空");
            }
            if (salesmanId == null) {
                return ApiResponse.error(400, "业务员ID不能为空");
            }
            productionService.batchAssignSalesman(deviceIds, salesmanId);
            return ApiResponse.success(null);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 移除设备的业务员分配
     */
    @DeleteMapping("/devices/{deviceId}/salesman")
    public ApiResponse<Void> removeSalesmanAssignment(@PathVariable String deviceId) {
        try {
            productionService.removeSalesmanAssignment(deviceId);
            return ApiResponse.success(null);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    // ==================== 扫码分配经销商接口 ====================

    /**
     * 批量扫码分配经销商
     * 支持批量输入设备ID，同时设置抽佣比例
     * 权限说明：只能分配当前用户设备管理列表中的设备
     * 请求体：{
     *   "deviceIds": ["A110000000000001", "A110000000000002"],
     *   "dealerCode": "01",
     *   "commissionRate": 30.00
     * }
     */
    @PostMapping("/devices/batch-assign-dealer")
    public ApiResponse<Map<String, Object>> batchAssignDealer(
            @RequestAttribute(value = "currentUserId", required = false) Long currentUserId,
            @RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<String> deviceIds = (List<String>) body.get("deviceIds");
            String dealerCode = (String) body.get("dealerCode");
            java.math.BigDecimal commissionRate = body.get("commissionRate") != null
                    ? new java.math.BigDecimal(body.get("commissionRate").toString())
                    : null;

            if (deviceIds == null || deviceIds.isEmpty()) {
                return ApiResponse.error(400, "设备ID列表不能为空");
            }
            if (dealerCode == null || dealerCode.length() != 2) {
                return ApiResponse.error(400, "经销商代码必须是2位");
            }

            Map<String, Object> result = productionService.batchAssignDealer(currentUserId, deviceIds, dealerCode, commissionRate);
            return ApiResponse.success(result);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/devices/check-scan-assign-dealer")
    public ApiResponse<Map<String, Object>> checkScanAssignDealer(
            @RequestAttribute(value = "currentUserId", required = false) Long currentUserId,
            @RequestBody Map<String, String> body) {
        try {
            String deviceId = body.get("deviceId");
            String vendorCode = body.get("vendorCode");
            if (deviceId == null || deviceId.trim().isEmpty()) {
                return ApiResponse.error(400, "设备ID不能为空");
            }
            if (vendorCode == null || vendorCode.length() != 2) {
                return ApiResponse.error(400, "经销商代码必须是2位");
            }
            Map<String, Object> result = productionService.checkScanAssignDealer(currentUserId, deviceId.trim(), vendorCode);
            return ApiResponse.success(result);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 扫码分配经销商（单个，兼容旧接口）
     * 根据设备ID和经销商代码，更新设备的经销商关联（不修改设备ID）
     * 请求体：{ "deviceId": "A110000000000001", "vendorCode": "01" }
     */
    @PostMapping("/devices/scan-assign-dealer")
    public ApiResponse<Map<String, Object>> scanAssignDealer(
            @RequestAttribute(value = "currentUserId", required = false) Long currentUserId,
            @RequestBody Map<String, String> body) {
        try {
            String deviceId = body.get("deviceId");
            String vendorCode = body.get("vendorCode");
            if (deviceId == null || deviceId.trim().isEmpty()) {
                return ApiResponse.error(400, "设备ID不能为空");
            }
            if (vendorCode == null || vendorCode.length() != 2) {
                return ApiResponse.error(400, "经销商代码必须是2位");
            }
            Map<String, Object> result = productionService.scanAssignDealer(currentUserId, deviceId.trim(), vendorCode);
            return ApiResponse.success(result);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    // ==================== 设备禁用/启用接口 ====================

    /**
     * 禁用/启用设备
     * 请求体：{ "status": "disabled" } 或 { "status": "manufactured" }
     */
    @PutMapping("/devices/{deviceId}/status")
    public ApiResponse<Map<String, Object>> updateDeviceStatus(
            @PathVariable String deviceId,
            @RequestBody Map<String, String> body) {
        try {
            String status = body.get("status");
            if (status == null || status.trim().isEmpty()) {
                return ApiResponse.error(400, "状态不能为空");
            }
            ManufacturedDevice device = productionService.updateDeviceStatus(deviceId, status);
            Map<String, Object> result = new HashMap<>();
            result.put("deviceId", device.getDeviceId());
            result.put("status", device.getStatus() != null ? device.getStatus().getCode() : null);
            result.put("message", "状态更新成功");
            return ApiResponse.success(result);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    // ==================== 设备激活接口 ====================

    /**
     * 激活设备
     * 用于用户绑定设备时调用，记录MAC地址、激活时间、上线国家
     * 请求体：{ "deviceId": "A110000000000001", "macAddress": "00:11:22:33:44:55", "country": "CN" }
     */
    @PostMapping("/devices/activate")
    public ApiResponse<Map<String, Object>> activateDevice(@RequestBody Map<String, String> body) {
        try {
            String deviceId = body.get("deviceId");
            String macAddress = body.get("macAddress");
            String country = body.get("country");
            if (deviceId == null || deviceId.trim().isEmpty()) {
                return ApiResponse.error(400, "设备ID不能为空");
            }
            ManufacturedDevice device = productionService.activateDevice(deviceId.trim(), macAddress, country);
            
            Map<String, Object> result = new HashMap<>();
            result.put("deviceId", device.getDeviceId());
            result.put("macAddress", device.getMacAddress());
            result.put("country", device.getCountry());
            result.put("activatedAt", device.getActivatedAt());
            result.put("status", device.getStatus() != null ? device.getStatus().getCode() : null);
            result.put("message", "设备激活成功");
            return ApiResponse.success(result);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
