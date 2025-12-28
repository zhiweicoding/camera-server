package com.pura365.camera.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.domain.Vendor;
import com.pura365.camera.enums.EnableStatus;
import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.repository.VendorRepository;
import com.pura365.camera.service.DeviceProductionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 经销商管理接口
 * 注意：新增经销商功能已移至用户管理模块
 */
@Tag(name = "经销商管理", description = "经销商信息管理相关接口")
@RestController
@RequestMapping("/api/admin/vendors")
public class VendorController {

    @Autowired
    private DeviceProductionService productionService;

    @Autowired
    private VendorRepository vendorRepository;

    /**
     * 分页查询经销商列表
     */
    @Operation(summary = "分页查询经销商", description = "分页查询经销商列表，支持按装机商、名称、状态筛选")
    @GetMapping
    public ApiResponse<Map<String, Object>> listVendors(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") Integer size,
            @Parameter(description = "装机商ID") @RequestParam(required = false) Long installerId,
            @Parameter(description = "经销商名称") @RequestParam(required = false) String name,
            @Parameter(description = "状态") @RequestParam(required = false) Integer status) {
        
        QueryWrapper<Vendor> qw = new QueryWrapper<>();
        if (installerId != null) {
            qw.lambda().eq(Vendor::getInstallerId, installerId);
        }
        if (name != null && !name.trim().isEmpty()) {
            qw.lambda().like(Vendor::getVendorName, name);
        }
        if (status != null) {
            qw.lambda().eq(Vendor::getStatus, EnableStatus.fromCode(status));
        }
        qw.lambda().orderByDesc(Vendor::getCreatedAt);

        // 分页查询
        int offset = (page - 1) * size;
        qw.last("LIMIT " + offset + ", " + size);
        List<Vendor> list = vendorRepository.selectList(qw);

        // 查询总数
        QueryWrapper<Vendor> countQw = new QueryWrapper<>();
        if (installerId != null) {
            countQw.lambda().eq(Vendor::getInstallerId, installerId);
        }
        if (name != null && !name.trim().isEmpty()) {
            countQw.lambda().like(Vendor::getVendorName, name);
        }
        if (status != null) {
            countQw.lambda().eq(Vendor::getStatus, EnableStatus.fromCode(status));
        }
        long total = vendorRepository.selectCount(countQw);

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return ApiResponse.success(result);
    }

    /**
     * 获取启用的经销商列表（不分页）
     */
    @Operation(summary = "获取启用的经销商列表", description = "获取所有状态为启用的经销商")
    @GetMapping("/enabled")
    public ApiResponse<List<Vendor>> listEnabledVendors() {
        return ApiResponse.success(productionService.listVendors());
    }

    /**
     * 按装机商获取经销商列表
     */
    @Operation(summary = "按装机商获取经销商", description = "获取指定装机商下的经销商列表")
    @GetMapping("/installer/{installerId}")
    public ApiResponse<List<Vendor>> listByInstaller(@PathVariable Long installerId) {
        QueryWrapper<Vendor> qw = new QueryWrapper<>();
        qw.lambda().eq(Vendor::getInstallerId, installerId)
                .eq(Vendor::getStatus, EnableStatus.ENABLED)
                .orderByAsc(Vendor::getVendorCode);
        return ApiResponse.success(vendorRepository.selectList(qw));
    }

    /**
     * 获取所有经销商列表(包含禁用的)
     */
    @Operation(summary = "获取所有经销商", description = "获取所有经销商列表(包含已禁用的)")
    @GetMapping("/all")
    public ApiResponse<List<Vendor>> listAllVendors() {
        return ApiResponse.success(productionService.listAllVendors());
    }

    /**
     * 获取单个经销商详情
     */
    @Operation(summary = "获取经销商详情", description = "根据ID获取经销商详细信息")
    @GetMapping("/{id}")
    public ApiResponse<Vendor> getVendor(@PathVariable Long id) {
        Vendor vendor = productionService.getVendorById(id);
        if (vendor == null) {
            return ApiResponse.error(404, "经销商不存在");
        }
        return ApiResponse.success(vendor);
    }

    /**
     * 新增经销商
     * 注意：此接口已废弃，新增经销商请通过用户管理模块创建
     * @deprecated 使用 /api/admin/users 接口创建经销商用户
     */
    @Deprecated
    @Operation(summary = "新增经销商（已废弃）", description = "请使用用户管理模块创建经销商")
    @PostMapping
    public ApiResponse<Vendor> createVendor(@RequestBody Vendor vendor) {
        return ApiResponse.error(400, "新增经销商请通过用户管理模块创建");
    }

    /**
     * 更新经销商信息
     */
    @Operation(summary = "更新经销商", description = "更新经销商信息")
    @PutMapping("/{id}")
    public ApiResponse<Vendor> updateVendor(@PathVariable Long id, @RequestBody Vendor vendor) {
        try {
            vendor.setId(id);
            Vendor updated = productionService.updateVendor(vendor);
            return ApiResponse.success(updated);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 删除经销商
     */
    @Operation(summary = "删除经销商", description = "删除指定的经销商")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteVendor(@PathVariable Long id) {
        try {
            productionService.deleteVendor(id);
            return ApiResponse.success(null);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 启用/禁用经销商
     * 请求体:{ "status": 1 } 或 { "status": 0 }
     */
    @Operation(summary = "更新经销商状态", description = "启用或禁用经销商")
    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateVendorStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        try {
            Integer status = body.get("status");
            if (status == null) {
                return ApiResponse.error(400, "status 不能为空");
            }
            productionService.updateVendorStatus(id, status);
            return ApiResponse.success(null);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}