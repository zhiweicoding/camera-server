package com.pura365.camera.controller.admin;

import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.model.report.PageResult;
import com.pura365.camera.model.report.PlanCommissionRequest;
import com.pura365.camera.model.report.PlanCommissionVO;
import com.pura365.camera.service.PlanCommissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 套餐分润配置管理接口
 */
@Tag(name = "套餐分润配置", description = "套餐分润配置的增删改查接口")
@RestController
@RequestMapping("/api/admin/plan-commissions")
public class PlanCommissionController {

    private static final Logger log = LoggerFactory.getLogger(PlanCommissionController.class);

    @Autowired
    private PlanCommissionService commissionService;

    /**
     * 分页查询套餐分润配置
     */
    @Operation(summary = "分页查询套餐分润配置", description = "分页查询套餐分润配置列表，支持按套餐ID、类型、状态筛选")
    @GetMapping
    public ApiResponse<PageResult<PlanCommissionVO>> listCommissions(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") Integer size,
            @Parameter(description = "套餐ID") @RequestParam(required = false) String planId,
            @Parameter(description = "套餐类型") @RequestParam(required = false) String planType,
            @Parameter(description = "状态") @RequestParam(required = false) Integer status) {
        log.info("分页查询套餐分润配置: page={}, size={}, planId={}, planType={}, status={}",
                page, size, planId, planType, status);
        PageResult<PlanCommissionVO> result = commissionService.listCommissions(page, size, planId, planType, status);
        return ApiResponse.success(result);
    }

    /**
     * 获取所有启用的分润配置
     */
    @Operation(summary = "获取所有启用的分润配置", description = "获取所有状态为启用的套餐分润配置")
    @GetMapping("/active")
    public ApiResponse<List<PlanCommissionVO>> listActiveCommissions() {
        log.info("获取所有启用的分润配置");
        return ApiResponse.success(commissionService.listActiveCommissions());
    }

    /**
     * 根据ID获取分润配置详情
     */
    @Operation(summary = "获取分润配置详情", description = "根据ID获取套餐分润配置详细信息")
    @GetMapping("/{id}")
    public ApiResponse<PlanCommissionVO> getCommission(
            @Parameter(description = "配置ID") @PathVariable Long id) {
        log.info("获取分润配置详情: id={}", id);
        PlanCommissionVO vo = commissionService.getById(id);
        if (vo == null) {
            return ApiResponse.error(404, "分润配置不存在");
        }
        return ApiResponse.success(vo);
    }

    /**
     * 根据套餐ID获取分润配置
     */
    @Operation(summary = "根据套餐ID获取分润配置", description = "根据套餐ID获取分润配置详情")
    @GetMapping("/plan/{planId}")
    public ApiResponse<PlanCommissionVO> getByPlanId(
            @Parameter(description = "套餐ID") @PathVariable String planId) {
        log.info("根据套餐ID获取分润配置: planId={}", planId);
        PlanCommissionVO vo = commissionService.getVOByPlanId(planId);
        if (vo == null) {
            return ApiResponse.error(404, "该套餐的分润配置不存在");
        }
        return ApiResponse.success(vo);
    }

    /**
     * 创建分润配置
     */
    @Operation(summary = "创建分润配置", description = "为套餐创建分润配置")
    @PostMapping
    public ApiResponse<PlanCommissionVO> createCommission(
            @Validated @RequestBody PlanCommissionRequest request) {
        log.info("创建分润配置: planId={}", request.getPlanId());
        try {
            PlanCommissionVO vo = commissionService.create(request);
            return ApiResponse.success(vo);
        } catch (RuntimeException e) {
            log.error("创建分润配置失败: {}", e.getMessage());
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 更新分润配置
     */
    @Operation(summary = "更新分润配置", description = "更新套餐分润配置信息")
    @PutMapping("/{id}")
    public ApiResponse<PlanCommissionVO> updateCommission(
            @Parameter(description = "配置ID") @PathVariable Long id,
            @RequestBody PlanCommissionRequest request) {
        log.info("更新分润配置: id={}", id);
        try {
            PlanCommissionVO vo = commissionService.update(id, request);
            return ApiResponse.success(vo);
        } catch (RuntimeException e) {
            log.error("更新分润配置失败: {}", e.getMessage());
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 删除分润配置
     */
    @Operation(summary = "删除分润配置", description = "删除指定的套餐分润配置")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteCommission(
            @Parameter(description = "配置ID") @PathVariable Long id) {
        log.info("删除分润配置: id={}", id);
        try {
            commissionService.delete(id);
            return ApiResponse.success(null);
        } catch (RuntimeException e) {
            log.error("删除分润配置失败: {}", e.getMessage());
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 更新分润配置状态
     */
    @Operation(summary = "更新分润配置状态", description = "启用或禁用套餐分润配置")
    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(
            @Parameter(description = "配置ID") @PathVariable Long id,
            @RequestBody Map<String, Integer> body) {
        log.info("更新分润配置状态: id={}", id);
        try {
            Integer status = body.get("status");
            if (status == null) {
                return ApiResponse.error(400, "status不能为空");
            }
            commissionService.updateStatus(id, status);
            return ApiResponse.success(null);
        } catch (RuntimeException e) {
            log.error("更新分润配置状态失败: {}", e.getMessage());
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
