package com.pura365.camera.controller.admin;

import com.pura365.camera.domain.Installer;
import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.service.InstallerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 装机商管理接口
 */
@Tag(name = "装机商管理", description = "装机商信息管理相关接口")
@RestController
@RequestMapping("/api/admin/installers")
public class InstallerController {

    @Autowired
    private InstallerService installerService;

    /**
     * 分页查询装机商列表
     */
    @Operation(summary = "分页查询装机商", description = "分页查询装机商列表，支持按代码、名称、状态筛选")
    @GetMapping
    public ApiResponse<Map<String, Object>> listInstallers(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") Integer size,
            @Parameter(description = "装机商代码") @RequestParam(required = false) String installerCode,
            @Parameter(description = "装机商名称") @RequestParam(required = false) String name,
            @Parameter(description = "状态：0-禁用 1-启用") @RequestParam(required = false) Integer status) {
        return ApiResponse.success(installerService.listInstallers(page, size, installerCode, name, status));
    }

    /**
     * 获取所有启用的装机商
     */
    @Operation(summary = "获取启用的装机商列表", description = "获取所有状态为启用的装机商")
    @GetMapping("/enabled")
    public ApiResponse<List<Installer>> listEnabled() {
        return ApiResponse.success(installerService.listEnabled());
    }

    /**
     * 获取所有装机商（包含禁用的）
     */
    @Operation(summary = "获取所有装机商", description = "获取所有装机商列表（包含已禁用的）")
    @GetMapping("/all")
    public ApiResponse<List<Installer>> listAll() {
        return ApiResponse.success(installerService.listAll());
    }

    /**
     * 获取单个装机商详情
     */
    @Operation(summary = "获取装机商详情", description = "根据ID获取装机商详细信息")
    @GetMapping("/{id}")
    public ApiResponse<Installer> getInstaller(@PathVariable Long id) {
        Installer installer = installerService.getById(id);
        if (installer == null) {
            return ApiResponse.error(404, "装机商不存在");
        }
        return ApiResponse.success(installer);
    }

    /**
     * 新增装机商
     * 请求体示例:
     * {
     *   "installerCode": "01",
     *   "installerName": "装机商名称",
     *   "contactPerson": "联系人",
     *   "contactPhone": "13800138000",
     *   "address": "地址",
     *   "commissionRate": 5.00
     * }
     */
    @Operation(summary = "新增装机商", description = "创建新的装机商信息")
    @PostMapping
    public ApiResponse<Installer> createInstaller(@RequestBody Installer installer) {
        try {
            Installer created = installerService.create(installer);
            return ApiResponse.success(created);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 更新装机商信息
     */
    @Operation(summary = "更新装机商", description = "更新装机商信息")
    @PutMapping("/{id}")
    public ApiResponse<Installer> updateInstaller(@PathVariable Long id, @RequestBody Installer installer) {
        try {
            Installer updated = installerService.update(id, installer);
            return ApiResponse.success(updated);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 删除装机商
     */
    @Operation(summary = "删除装机商", description = "删除指定的装机商")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteInstaller(@PathVariable Long id) {
        try {
            installerService.delete(id);
            return ApiResponse.success(null);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 启用/禁用装机商
     * 请求体: { "status": 1 } 或 { "status": 0 }
     */
    @Operation(summary = "更新装机商状态", description = "启用或禁用装机商")
    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        try {
            Integer status = body.get("status");
            if (status == null) {
                return ApiResponse.error(400, "status 不能为空");
            }
            installerService.updateStatus(id, status);
            return ApiResponse.success(null);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
