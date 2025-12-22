package com.pura365.camera.controller.admin;

import com.pura365.camera.domain.Salesman;
import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.service.SalesmanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 业务员管理接口
 */
@Tag(name = "业务员管理", description = "业务员信息管理相关接口")
@RestController
@RequestMapping("/api/admin/salesman")
public class SalesmanController {

    @Autowired
    private SalesmanService salesmanService;

    /**
     * 分页查询业务员列表
     */
    @Operation(summary = "分页查询业务员", description = "分页查询业务员列表，支持按经销商、姓名、状态筛选")
    @GetMapping
    public ApiResponse<Map<String, Object>> listSalesmen(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) String vendorCode,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.success(salesmanService.listSalesmen(page, size, vendorId, vendorCode, name, status));
    }

    /**
     * 获取经销商下的业务员列表
     */
    @Operation(summary = "获取经销商下的业务员", description = "获取指定经销商下所有启用的业务员")
    @GetMapping("/vendor/{vendorId}")
    public ApiResponse<List<Salesman>> listByVendor(@PathVariable Long vendorId) {
        return ApiResponse.success(salesmanService.listActiveByVendor(vendorId));
    }

    /**
     * 根据经销商代码获取业务员列表
     */
    @Operation(summary = "根据经销商代码获取业务员", description = "根据经销商代码获取启用的业务员列表")
    @GetMapping("/vendor-code/{vendorCode}")
    public ApiResponse<List<Salesman>> listByVendorCode(@PathVariable String vendorCode) {
        return ApiResponse.success(salesmanService.listByVendorCode(vendorCode));
    }

    /**
     * 获取单个业务员详情
     */
    @Operation(summary = "获取业务员详情", description = "根据ID获取业务员详细信息")
    @GetMapping("/{id}")
    public ApiResponse<Salesman> getSalesman(@PathVariable Long id) {
        Salesman salesman = salesmanService.getById(id);
        if (salesman == null) {
            return ApiResponse.error(404, "业务员不存在");
        }
        return ApiResponse.success(salesman);
    }

    /**
     * 新增业务员
     */
    @Operation(summary = "新增业务员", description = "创建新的业务员")
    @PostMapping
    public ApiResponse<Salesman> createSalesman(@RequestBody Salesman salesman) {
        try {
            Salesman created = salesmanService.create(salesman);
            return ApiResponse.success(created);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 更新业务员信息
     */
    @Operation(summary = "更新业务员", description = "更新业务员信息")
    @PutMapping("/{id}")
    public ApiResponse<Salesman> updateSalesman(@PathVariable Long id, @RequestBody Salesman salesman) {
        try {
            Salesman updated = salesmanService.update(id, salesman);
            return ApiResponse.success(updated);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 删除业务员
     */
    @Operation(summary = "删除业务员", description = "删除指定的业务员")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteSalesman(@PathVariable Long id) {
        try {
            salesmanService.delete(id);
            return ApiResponse.success(null);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 启用/禁用业务员
     */
    @Operation(summary = "更新业务员状态", description = "启用或禁用业务员")
    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        try {
            Integer status = body.get("status");
            if (status == null) {
                return ApiResponse.error(400, "status 不能为空");
            }
            salesmanService.updateStatus(id, status);
            return ApiResponse.success(null);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
