package com.pura365.camera.controller.admin;

import com.pura365.camera.domain.CloudPlan;
import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.service.CloudPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 套餐管理接口
 */
@Tag(name = "套餐管理", description = "云存储套餐管理相关接口")
@RestController
@RequestMapping("/api/admin/cloud-plans")
public class CloudPlanController {

    @Autowired
    private CloudPlanService planService;

    /**
     * 分页查询套餐列表
     */
    @Operation(summary = "分页查询套餐", description = "分页查询套餐列表，支持按类型、名称、状态筛选")
    @GetMapping
    public ApiResponse<Map<String, Object>> listPlans(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status) {
        try {
            return ApiResponse.success(planService.listPlans(page, size, type, name, status));
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 获取所有启用的套餐
     */
    @Operation(summary = "获取启用的套餐", description = "获取所有启用的套餐列表")
    @GetMapping("/active")
    public ApiResponse<List<CloudPlan>> listActivePlans() {
        return ApiResponse.success(planService.listActivePlans());
    }

    /**
     * 按类型获取启用的套餐
     */
    @Operation(summary = "按类型获取套餐", description = "获取指定类型的启用套餐")
    @GetMapping("/type/{type}")
    public ApiResponse<List<CloudPlan>> listByType(@PathVariable String type) {
        return ApiResponse.success(planService.listActivePlansByType(type));
    }

    /**
     * 获取套餐类型列表
     */
    @Operation(summary = "获取套餐类型", description = "获取所有套餐类型")
    @GetMapping("/types")
    public ApiResponse<List<Map<String, String>>> listTypes() {
        return ApiResponse.success(planService.listTypes());
    }

    /**
     * 获取单个套餐详情
     */
    @Operation(summary = "获取套餐详情", description = "根据ID获取套餐详细信息")
    @GetMapping("/{id}")
    public ApiResponse<CloudPlan> getPlan(@PathVariable Long id) {
        CloudPlan plan = planService.getById(id);
        if (plan == null) {
            return ApiResponse.error(404, "套餐不存在");
        }
        return ApiResponse.success(plan);
    }

    /**
     * 根据planId获取套餐
     */
    @Operation(summary = "根据planId获取套餐", description = "根据套餐ID获取套餐详情")
    @GetMapping("/plan-id/{planId}")
    public ApiResponse<CloudPlan> getByPlanId(@PathVariable String planId) {
        CloudPlan plan = planService.getByPlanId(planId);
        if (plan == null) {
            return ApiResponse.error(404, "套餐不存在");
        }
        return ApiResponse.success(plan);
    }

    /**
     * 新增套餐
     */
    @Operation(summary = "新增套餐", description = "创建新的套餐")
    @PostMapping
    public ApiResponse<CloudPlan> createPlan(@RequestBody CloudPlan plan) {
        try {
            CloudPlan created = planService.create(plan);
            return ApiResponse.success(created);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 更新套餐信息
     */
    @Operation(summary = "更新套餐", description = "更新套餐信息")
    @PutMapping("/{id}")
    public ApiResponse<CloudPlan> updatePlan(@PathVariable Long id, @RequestBody CloudPlan plan) {
        try {
            CloudPlan updated = planService.update(id, plan);
            return ApiResponse.success(updated);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 删除套餐
     */
    @Operation(summary = "删除套餐", description = "删除指定的套餐")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePlan(@PathVariable Long id) {
        try {
            planService.delete(id);
            return ApiResponse.success(null);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 启用/禁用套餐
     */
    @Operation(summary = "更新套餐状态", description = "启用或禁用套餐")
    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        try {
            Integer status = body.get("status");
            if (status == null) {
                return ApiResponse.error(400, "status 不能为空");
            }
            planService.updateStatus(id, status);
            return ApiResponse.success(null);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 批量更新排序
     */
    @Operation(summary = "批量更新排序", description = "批量更新套餐排序")
    @PutMapping("/sort-order")
    public ApiResponse<Void> updateSortOrder(@RequestBody List<Map<String, Object>> items) {
        try {
            planService.updateSortOrder(items);
            return ApiResponse.success(null);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
