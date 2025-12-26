package com.pura365.camera.controller.admin;

import com.pura365.camera.domain.SysDict;
import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.service.SysDictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 字典管理接口
 */
@Tag(name = "字典管理", description = "系统字典管理相关接口")
@RestController
@RequestMapping("/api/admin/dict")
public class SysDictController {

    @Autowired
    private SysDictService dictService;

    /**
     * 分页查询字典项列表
     */
    @Operation(summary = "分页查询字典项", description = "分页查询字典项列表，支持按分类、代码、状态筛选")
    @GetMapping
    public ApiResponse<Map<String, Object>> listDicts(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.success(dictService.listDicts(page, size, category, code, status));
    }

    /**
     * 获取字典分类列表
     */
    @Operation(summary = "获取字典分类", description = "获取所有字典分类")
    @GetMapping("/categories")
    public ApiResponse<List<String>> listCategories() {
        return ApiResponse.success(dictService.listCategories());
    }

    /**
     * 根据分类获取字典项（启用状态）
     */
    @Operation(summary = "根据分类获取字典项", description = "获取指定分类下启用的字典项")
    @GetMapping("/category/{category}")
    public ApiResponse<List<SysDict>> listByCategory(@PathVariable String category) {
        return ApiResponse.success(dictService.listByCategory(category));
    }

    /**
     * 根据分类获取所有字典项（包含禁用）
     */
    @Operation(summary = "获取分类下所有字典项", description = "获取指定分类下所有字典项（包含禁用）")
    @GetMapping("/category/{category}/all")
    public ApiResponse<List<SysDict>> listAllByCategory(@PathVariable String category) {
        return ApiResponse.success(dictService.listAllByCategory(category));
    }

    /**
     * 获取设备ID生成选项（从字典表读取）
     */
    @Operation(summary = "获取设备ID生成选项", description = "获取设备ID各位置的可选值")
    @GetMapping("/device-id-options")
    public ApiResponse<Map<String, List<Map<String, String>>>> getDeviceIdOptions() {
        return ApiResponse.success(dictService.getDeviceIdOptions());
    }

    /**
     * 获取单个字典项详情
     */
    @Operation(summary = "获取字典项详情", description = "根据ID获取字典项详细信息")
    @GetMapping("/{id}")
    public ApiResponse<SysDict> getDict(@PathVariable Long id) {
        SysDict dict = dictService.getById(id);
        if (dict == null) {
            return ApiResponse.error(404, "字典项不存在");
        }
        return ApiResponse.success(dict);
    }

    /**
     * 新增字典项
     */
    @Operation(summary = "新增字典项", description = "创建新的字典项")
    @PostMapping
    public ApiResponse<SysDict> createDict(@RequestBody SysDict dict) {
        try {
            SysDict created = dictService.create(dict);
            return ApiResponse.success(created);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 更新字典项信息
     */
    @Operation(summary = "更新字典项", description = "更新字典项信息")
    @PutMapping("/{id}")
    public ApiResponse<SysDict> updateDict(@PathVariable Long id, @RequestBody SysDict dict) {
        try {
            SysDict updated = dictService.update(id, dict);
            return ApiResponse.success(updated);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 删除字典项
     */
    @Operation(summary = "删除字典项", description = "删除指定的字典项")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteDict(@PathVariable Long id) {
        try {
            dictService.delete(id);
            return ApiResponse.success(null);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 启用/禁用字典项
     */
    @Operation(summary = "更新字典项状态", description = "启用或禁用字典项")
    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        try {
            Integer status = body.get("status");
            if (status == null) {
                return ApiResponse.error(400, "status 不能为空");
            }
            dictService.updateStatus(id, status);
            return ApiResponse.success(null);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 批量更新排序
     */
    @Operation(summary = "批量更新排序", description = "批量更新字典项排序")
    @PutMapping("/sort-order")
    public ApiResponse<Void> updateSortOrder(@RequestBody List<Map<String, Object>> items) {
        try {
            dictService.updateSortOrder(items);
            return ApiResponse.success(null);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
