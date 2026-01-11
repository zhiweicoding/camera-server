package com.pura365.camera.controller.admin;

import com.pura365.camera.domain.Dealer;
import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.service.DealerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 经销商管理接口
 */
@Tag(name = "经销商管理", description = "经销商信息管理相关接口")
@RestController
@RequestMapping("/api/admin/dealers")
public class DealerController {

    @Autowired
    private DealerService dealerService;

    /**
     * 分页查询经销商列表
     */
    @Operation(summary = "分页查询经销商", description = "分页查询经销商列表，支持按装机商、名称、状态筛选")
    @GetMapping
    public ApiResponse<Map<String, Object>> listDealers(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) Long installerId,
            @RequestParam(required = false) String installerCode,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.success(dealerService.listDealers(page, size, installerId, installerCode, name, status));
    }

    /**
     * 获取所有启用的经销商列表
     */
    @Operation(summary = "获取所有启用的经销商", description = "获取所有启用的经销商列表")
    @GetMapping("/active")
    public ApiResponse<List<Dealer>> listActive() {
        return ApiResponse.success(dealerService.listAllActive());
    }

    /**
     * 获取单个经销商详情
     */
    @Operation(summary = "获取经销商详情", description = "根据ID获取经销商详细信息")
    @GetMapping("/{id}")
    public ApiResponse<Dealer> getDealer(@PathVariable Long id) {
        Dealer dealer = dealerService.getById(id);
        if (dealer == null) {
            return ApiResponse.error(404, "经销商不存在");
        }
        return ApiResponse.success(dealer);
    }

    /**
     * 新增经销商
     */
    @Operation(summary = "新增经销商", description = "创建新的经销商")
    @PostMapping
    public ApiResponse<Dealer> createDealer(@RequestBody Dealer dealer) {
        try {
            Dealer created = dealerService.create(dealer);
            return ApiResponse.success(created);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 更新经销商信息
     */
    @Operation(summary = "更新经销商", description = "更新经销商信息")
    @PutMapping("/{id}")
    public ApiResponse<Dealer> updateDealer(@PathVariable Long id, @RequestBody Dealer dealer) {
        try {
            Dealer updated = dealerService.update(id, dealer);
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
    public ApiResponse<Void> deleteDealer(@PathVariable Long id) {
        try {
            dealerService.delete(id);
            return ApiResponse.success(null);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 启用/禁用经销商
     */
    @Operation(summary = "更新经销商状态", description = "启用或禁用经销商")
    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        try {
            Integer status = body.get("status");
            if (status == null) {
                return ApiResponse.error(400, "status 不能为空");
            }
            dealerService.updateStatus(id, status);
            return ApiResponse.success(null);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
