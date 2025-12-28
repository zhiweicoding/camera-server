package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.domain.CloudPlan;
import com.pura365.camera.domain.PlanCommission;
import com.pura365.camera.enums.CommissionFeeType;
import com.pura365.camera.enums.CommissionProfitMode;
import com.pura365.camera.enums.EnableStatus;
import com.pura365.camera.model.report.PageResult;
import com.pura365.camera.model.report.PlanCommissionRequest;
import com.pura365.camera.model.report.PlanCommissionVO;
import com.pura365.camera.repository.CloudPlanRepository;
import com.pura365.camera.repository.PlanCommissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 套餐分润配置服务
 */
@Service
public class PlanCommissionService {

    private static final Logger log = LoggerFactory.getLogger(PlanCommissionService.class);

    @Autowired
    private PlanCommissionRepository commissionRepository;

    @Autowired
    private CloudPlanRepository planRepository;

    /**
     * 分页查询套餐分润配置
     *
     * @param page     页码
     * @param size     每页数量
     * @param planId   套餐ID（可选）
     * @param planType 套餐类型（可选）
     * @param status   状态（可选）
     */
    public PageResult<PlanCommissionVO> listCommissions(Integer page, Integer size,
                                                         String planId, String planType, Integer status) {
        QueryWrapper<PlanCommission> qw = new QueryWrapper<>();

        if (planId != null && !planId.trim().isEmpty()) {
            qw.lambda().like(PlanCommission::getPlanId, planId);
        }
        if (status != null) {
            qw.lambda().eq(PlanCommission::getStatus, EnableStatus.fromCode(status));
        }
        qw.lambda().orderByDesc(PlanCommission::getCreatedAt);

        // 分页查询
        int offset = (page - 1) * size;
        qw.last("LIMIT " + offset + ", " + size);
        List<PlanCommission> list = commissionRepository.selectList(qw);

        // 查询总数
        QueryWrapper<PlanCommission> countQw = new QueryWrapper<>();
        if (planId != null && !planId.trim().isEmpty()) {
            countQw.lambda().like(PlanCommission::getPlanId, planId);
        }
        if (status != null) {
            countQw.lambda().eq(PlanCommission::getStatus, EnableStatus.fromCode(status));
        }
        long total = commissionRepository.selectCount(countQw);

        // 转换为VO
        List<PlanCommissionVO> voList = new ArrayList<>();
        for (PlanCommission commission : list) {
            PlanCommissionVO vo = convertToVO(commission);
            // 过滤套餐类型
            if (planType != null && !planType.trim().isEmpty()) {
                if (!planType.equals(vo.getPlanType())) {
                    continue;
                }
            }
            voList.add(vo);
        }

        return PageResult.of(voList, total, page, size);
    }

    /**
     * 获取所有启用的套餐分润配置
     */
    public List<PlanCommissionVO> listActiveCommissions() {
        QueryWrapper<PlanCommission> qw = new QueryWrapper<>();
        qw.lambda().eq(PlanCommission::getStatus, EnableStatus.ENABLED);
        List<PlanCommission> list = commissionRepository.selectList(qw);

        List<PlanCommissionVO> voList = new ArrayList<>();
        for (PlanCommission commission : list) {
            voList.add(convertToVO(commission));
        }
        return voList;
    }

    /**
     * 根据ID获取套餐分润配置
     */
    public PlanCommissionVO getById(Long id) {
        PlanCommission commission = commissionRepository.selectById(id);
        if (commission == null) {
            return null;
        }
        return convertToVO(commission);
    }

    /**
     * 根据套餐ID获取分润配置
     */
    public PlanCommission getByPlanId(String planId) {
        QueryWrapper<PlanCommission> qw = new QueryWrapper<>();
        qw.lambda().eq(PlanCommission::getPlanId, planId);
        return commissionRepository.selectOne(qw);
    }

    /**
     * 根据套餐ID获取分润配置VO
     */
    public PlanCommissionVO getVOByPlanId(String planId) {
        PlanCommission commission = getByPlanId(planId);
        if (commission == null) {
            return null;
        }
        return convertToVO(commission);
    }

    /**
     * 创建套餐分润配置
     */
    @Transactional
    public PlanCommissionVO create(PlanCommissionRequest request) {
        // 校验套餐是否存在
        QueryWrapper<CloudPlan> planQw = new QueryWrapper<>();
        planQw.lambda().eq(CloudPlan::getPlanId, request.getPlanId());
        CloudPlan plan = planRepository.selectOne(planQw);
        if (plan == null) {
            throw new RuntimeException("套餐不存在: " + request.getPlanId());
        }

        // 检查是否已存在配置
        PlanCommission existing = getByPlanId(request.getPlanId());
        if (existing != null) {
            throw new RuntimeException("该套餐已存在分润配置，请使用更新接口");
        }

        PlanCommission commission = new PlanCommission();
        // 手动复制属性，避免类型不匹配
        commission.setPlanId(request.getPlanId());
        commission.setPayeeEntity(request.getPayeeEntity());
        commission.setFeeRate(request.getFeeRate());
        commission.setFeeFixed(request.getFeeFixed());
        commission.setRebateRate(request.getRebateRate());
        commission.setPlanCost(request.getPlanCost());
        commission.setInstallerRate(request.getInstallerRate());
        commission.setLevel1Rate(request.getLevel1Rate());
        commission.setLevel2Rate(request.getLevel2Rate());
        commission.setRemark(request.getRemark());
        commission.setCreatedAt(new Date());
        commission.setUpdatedAt(new Date());

        // 设置枚举类型字段（从字符串转换）
        commission.setStatus(request.getStatus() != null ? EnableStatus.fromCode(request.getStatus()) : EnableStatus.ENABLED);
        commission.setFeeType(request.getFeeType() != null ? CommissionFeeType.fromCode(request.getFeeType()) : CommissionFeeType.FIXED);
        commission.setProfitMode(request.getProfitMode() != null ? CommissionProfitMode.fromCode(request.getProfitMode()) : CommissionProfitMode.PROFIT);

        commissionRepository.insert(commission);
        log.info("创建套餐分润配置: planId={}, id={}", request.getPlanId(), commission.getId());

        return convertToVO(commission);
    }

    /**
     * 更新套餐分润配置
     */
    @Transactional
    public PlanCommissionVO update(Long id, PlanCommissionRequest request) {
        PlanCommission existing = commissionRepository.selectById(id);
        if (existing == null) {
            throw new RuntimeException("套餐分润配置不存在");
        }

        // 如果修改了planId，校验新的套餐是否存在
        if (request.getPlanId() != null && !request.getPlanId().equals(existing.getPlanId())) {
            QueryWrapper<CloudPlan> planQw = new QueryWrapper<>();
            planQw.lambda().eq(CloudPlan::getPlanId, request.getPlanId());
            CloudPlan plan = planRepository.selectOne(planQw);
            if (plan == null) {
                throw new RuntimeException("套餐不存在: " + request.getPlanId());
            }

            // 检查新planId是否已有配置
            PlanCommission existingForNewPlan = getByPlanId(request.getPlanId());
            if (existingForNewPlan != null && !existingForNewPlan.getId().equals(id)) {
                throw new RuntimeException("目标套餐已存在分润配置");
            }
        }

        // 更新字段（仅更新非空字段）
        if (request.getPlanId() != null) {
            existing.setPlanId(request.getPlanId());
        }
        if (request.getPayeeEntity() != null) {
            existing.setPayeeEntity(request.getPayeeEntity());
        }
        if (request.getFeeType() != null) {
            existing.setFeeType(CommissionFeeType.fromCode(request.getFeeType()));
        }
        if (request.getFeeRate() != null) {
            existing.setFeeRate(request.getFeeRate());
        }
        if (request.getFeeFixed() != null) {
            existing.setFeeFixed(request.getFeeFixed());
        }
        if (request.getRebateRate() != null) {
            existing.setRebateRate(request.getRebateRate());
        }
        if (request.getPlanCost() != null) {
            existing.setPlanCost(request.getPlanCost());
        }
        if (request.getProfitMode() != null) {
            existing.setProfitMode(CommissionProfitMode.fromCode(request.getProfitMode()));
        }
        if (request.getInstallerRate() != null) {
            existing.setInstallerRate(request.getInstallerRate());
        }
        if (request.getLevel1Rate() != null) {
            existing.setLevel1Rate(request.getLevel1Rate());
        }
        if (request.getLevel2Rate() != null) {
            existing.setLevel2Rate(request.getLevel2Rate());
        }
        if (request.getStatus() != null) {
            existing.setStatus(EnableStatus.fromCode(request.getStatus()));
        }
        if (request.getRemark() != null) {
            existing.setRemark(request.getRemark());
        }
        existing.setUpdatedAt(new Date());

        commissionRepository.updateById(existing);
        log.info("更新套餐分润配置: id={}", id);

        return convertToVO(existing);
    }

    /**
     * 删除套餐分润配置
     */
    @Transactional
    public void delete(Long id) {
        PlanCommission existing = commissionRepository.selectById(id);
        if (existing == null) {
            throw new RuntimeException("套餐分润配置不存在");
        }

        commissionRepository.deleteById(id);
        log.info("删除套餐分润配置: id={}, planId={}", id, existing.getPlanId());
    }

    /**
     * 更新状态
     */
    @Transactional
    public void updateStatus(Long id, Integer status) {
        PlanCommission existing = commissionRepository.selectById(id);
        if (existing == null) {
            throw new RuntimeException("套餐分润配置不存在");
        }

        existing.setStatus(EnableStatus.fromCode(status));
        existing.setUpdatedAt(new Date());
        commissionRepository.updateById(existing);
        log.info("更新套餐分润配置状态: id={}, status={}", id, status);
    }

    /**
     * 转换为VO对象
     */
    private PlanCommissionVO convertToVO(PlanCommission commission) {
        PlanCommissionVO vo = new PlanCommissionVO();
        // 手动设置基本字段（避免 BeanUtils 无法处理枚举类型转换）
        vo.setId(commission.getId());
        vo.setPlanId(commission.getPlanId());
        vo.setPayeeEntity(commission.getPayeeEntity());
        vo.setFeeRate(commission.getFeeRate());
        vo.setFeeFixed(commission.getFeeFixed());
        vo.setRebateRate(commission.getRebateRate());
        vo.setPlanCost(commission.getPlanCost());
        vo.setInstallerRate(commission.getInstallerRate());
        vo.setLevel1Rate(commission.getLevel1Rate());
        vo.setLevel2Rate(commission.getLevel2Rate());
        vo.setRemark(commission.getRemark());
        vo.setCreatedAt(commission.getCreatedAt());
        vo.setUpdatedAt(commission.getUpdatedAt());

        // 枚举类型字段转换为 String/Integer
        vo.setFeeType(commission.getFeeType() != null ? commission.getFeeType().getCode() : null);
        vo.setProfitMode(commission.getProfitMode() != null ? commission.getProfitMode().getCode() : null);
        vo.setStatus(commission.getStatus() != null ? commission.getStatus().getCode() : null);

        // 查询套餐信息
        QueryWrapper<CloudPlan> planQw = new QueryWrapper<>();
        planQw.lambda().eq(CloudPlan::getPlanId, commission.getPlanId());
        CloudPlan plan = planRepository.selectOne(planQw);
        if (plan != null) {
            vo.setPlanName(plan.getName());
            vo.setPlanType(plan.getType() != null ? plan.getType().getCode() : null);
            vo.setPlanTypeName(getPlanTypeName(plan.getType()));
            vo.setPlanPrice(plan.getPrice());
        }

        // 手续费描述
        vo.setFeeDesc(buildFeeDesc(commission));
        vo.setFeeTypeName(getFeeTypeName(commission.getFeeType()));

        // 分润模式名称
        vo.setProfitModeName(getProfitModeName(commission.getProfitMode()));

        // 状态名称
        vo.setStatusName(commission.getStatus() == EnableStatus.ENABLED ? "启用" : "禁用");

        return vo;
    }

    /**
     * 构建手续费描述
     */
    public String buildFeeDesc(PlanCommission commission) {
        if (commission == null) {
            return "-";
        }

        CommissionFeeType feeType = commission.getFeeType();
        BigDecimal feeRate = commission.getFeeRate();
        BigDecimal feeFixed = commission.getFeeFixed();

        if (CommissionFeeType.MIXED == feeType && feeRate != null && feeFixed != null) {
            // 混合类型：如 4.4% + 0.3USD
            return feeRate + "% + " + feeFixed + "USD";
        } else if (feeRate != null) {
            // 固定比例
            return feeRate + "%";
        }

        return "-";
    }

    /**
     * 获取套餐类型名称
     */
    public String getPlanTypeName(Object type) {
        if (type == null) {
            return "未知";
        }
        String typeStr = type.toString();
        switch (typeStr) {
            case "motion":
                return "动态录像";
            case "fulltime":
                return "全天录像";
            case "traffic":
                return "4G流量";
            default:
                return typeStr;
        }
    }

    /**
     * 获取手续费类型名称
     */
    private String getFeeTypeName(CommissionFeeType feeType) {
        if (feeType == null) {
            return "未知";
        }
        return feeType.getDescription();
    }

    /**
     * 获取分润模式名称
     */
    public String getProfitModeName(CommissionProfitMode profitMode) {
        if (profitMode == null) {
            return "未知";
        }
        return profitMode.getDescription();
    }
}
