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

    // ==================== 设备管理接口 ====================

    /**
     * 分页查询设备列表
     * 参数：
     * - page: 页码，从1开始
     * - size: 每页大小
     * - deviceId: 设备ID（模糊查询，可选）
     * - batchNo: 批次号（可选）
     * - status: 状态（可选）
     */
    @GetMapping("/devices")
    public ApiResponse<Map<String, Object>> listDevices(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String vendorCode) {
        Map<String, Object> result = productionService.listDevices(page, size, deviceId, batchNo, status, vendorCode);
        return ApiResponse.success(result);
    }

    /**
     * 获取单个设备详情
     */
    @GetMapping("/devices/{deviceId}")
    public ApiResponse<ManufacturedDevice> getDevice(@PathVariable String deviceId) {
        ManufacturedDevice device = productionService.getDevice(deviceId);
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
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String status) {
        try {
            List<ManufacturedDevice> devices = productionService.listAllDevices(deviceId, batchNo, status);
            
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
                row.createCell(10).setCellValue(device.getStatus());
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
}
