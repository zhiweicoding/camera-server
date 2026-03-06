package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.domain.*;
import com.pura365.camera.enums.CommissionFeeType;
import com.pura365.camera.enums.CommissionProfitMode;
import com.pura365.camera.repository.*;
import com.pura365.camera.util.PaymentFeeRuleUtil;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 分润计算服务
 * 
 * 分润计算逻辑（重构后）：
 * 1. 可分润金额 = 套餐价格 - 手续费 - 套餐成本
 * 2. 装机商分润 = 可分润金额 × 装机商比例（来自设备.installerCommissionRate）
 * 3. 经销商分润 = 可分润金额 × 经销商比例（来自设备.dealerCommissionRate）
 * 4. 公司利润 = 可分润金额 - 装机商分润 - 经销商分润
 * 
 * 分润比例来源：
 * - 装机商/经销商比例：创建生产批次时设置，存储在 manufactured_device 表
 * - 套餐配置：手续费、成本等，存储在 plan_commission 表
 */
@Service
public class CommissionCalculateService {

    private static final Logger log = LoggerFactory.getLogger(CommissionCalculateService.class);

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Autowired
    private ManufacturedDeviceRepository manufacturedDeviceRepository;

    @Autowired
    private InstallerRepository installerRepository;

    @Autowired
    private PlanCommissionRepository planCommissionRepository;

    @Autowired
    private CloudPlanRepository cloudPlanRepository;

    @Autowired
    private DeviceDealerRepository deviceDealerRepository;

    @Autowired
    private DealerRepository dealerRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * 分润明细
     */
    @Data
    public static class CommissionDetail {
        private String type;          // COMPANY, INSTALLER, VENDOR
        private Long entityId;        // 实体ID（装机商ID或经销商ID）
        private String entityCode;    // 实体代码
        private String entityName;    // 实体名称
        private BigDecimal rate;      // 分润比例
        private BigDecimal amount;    // 分润金额（基于上级利润计算）
        private BigDecimal actualAmount; // 实际所得金额（扣除下级分润后）
        private Integer level;        // 层级

        public CommissionDetail(String type, Long entityId, String entityCode, String entityName,
                                BigDecimal rate, BigDecimal amount, BigDecimal actualAmount, Integer level) {
            this.type = type;
            this.entityId = entityId;
            this.entityCode = entityCode;
            this.entityName = entityName;
            this.rate = rate;
            this.amount = amount;
            this.actualAmount = actualAmount;
            this.level = level;
        }
    }

    /**
     * 分润计算结果
     */
    @Data
    public static class CommissionResult {
        private BigDecimal payAmount;        // 支付金额
        private BigDecimal feeAmount;        // 手续费
        private BigDecimal planCost;         // 套餐成本
        private BigDecimal profitAmount;     // 可分润金额
        private BigDecimal companyAmount;    // 公司利润
        private BigDecimal installerBaseAmount; // 装机商基础利润（=实际所得，独立分润池）
        private BigDecimal installerActualAmount; // 装机商实际所得（与installerBaseAmount相同）
        private BigDecimal level1BaseAmount; // 一级经销商分润池（基于总可分利润）
        private Long installerId;            // 装机商ID
        private String installerCode;        // 装机商代码
        private List<CommissionDetail> details = new ArrayList<>(); // 分润明细
        
        // 双重身份合并后的结果
        private boolean hasDualIdentity;     // 是否存在双重身份
        private Long dualIdentityUserId;     // 双重身份用户ID
        private BigDecimal dualIdentityTotalAmount; // 双重身份合计金额
    }

    /**
     * 计算订单分润
     * 
     * @param deviceId 设备ID
     * @param payAmount 支付金额
     * @param planId 套餐ID
     * @return 分润计算结果
     */
    public CommissionResult calculateCommission(String deviceId, BigDecimal payAmount, String planId) {
        return calculateCommission(deviceId, payAmount, planId, null);
    }

    public CommissionResult calculateCommission(String deviceId, BigDecimal payAmount, String planId, String paymentMethod) {
        CommissionResult result = new CommissionResult();
        result.setPayAmount(payAmount);

        // 1. 获取套餐分润配置（手续费、成本等）
        PlanCommission planCommission = getPlanCommission(planId);
        if (planCommission == null) {
            log.warn("未找到套餐分润配置: planId={}，将使用默认值计算", planId);
            // 使用默认配置：手续费0，成本0
            planCommission = new PlanCommission();
            planCommission.setPlanId(planId);
            planCommission.setFeeRate(BigDecimal.ZERO);
            planCommission.setFeeFixed(BigDecimal.ZERO);
        }

        // 2. 获取设备信息（分润比例存储在设备级别）
        ManufacturedDevice device = getDeviceByDeviceId(deviceId);
        if (device == null) {
            log.warn("未找到设备: deviceId={}", deviceId);
            return result;
        }

        // 3. 计算手续费
        BigDecimal feeAmount = calculateFeeAmount(payAmount, planCommission, paymentMethod);
        result.setFeeAmount(feeAmount);

        // 4. 获取套餐成本（优先分润配置，其次回退套餐表）
        BigDecimal planCost = resolvePlanCost(planId, planCommission);
        result.setPlanCost(planCost);

        // 5. 计算可分润金额 = 支付金额 - 手续费 - 套餐成本
        CommissionProfitMode profitMode = planCommission.getProfitMode() != null
                ? planCommission.getProfitMode() : CommissionProfitMode.PROFIT;

        BigDecimal profitAmount = calculateProfitAmount(payAmount, feeAmount, planCost, profitMode);
        result.setProfitAmount(profitAmount);

        // 6. 从设备获取分润比例（来自创建批次时的设置）
        BigDecimal installerRate = device.getInstallerCommissionRate() != null 
                ? device.getInstallerCommissionRate() : BigDecimal.ZERO;
        BigDecimal dealerRate = device.getDealerCommissionRate() != null 
                ? device.getDealerCommissionRate() : BigDecimal.ZERO;

        // 7. 计算装机商分润 = 可分润金额 × 装机商比例
        BigDecimal installerAmount = profitAmount.multiply(installerRate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        result.setInstallerBaseAmount(installerAmount);
        result.setInstallerActualAmount(installerAmount);

        // 8. 计算经销商分润 = 可分润金额 × 经销商比例
        BigDecimal dealerAmount = profitAmount.multiply(dealerRate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        result.setLevel1BaseAmount(dealerAmount);

        // 9. 计算公司利润 = 可分润金额 - 装机商分润 - 经销商分润
        BigDecimal companyAmount = profitAmount.subtract(installerAmount).subtract(dealerAmount);
        if (companyAmount.compareTo(BigDecimal.ZERO) < 0) {
            companyAmount = BigDecimal.ZERO;
        }
        result.setCompanyAmount(companyAmount);

        // 10. 设置装机商信息
        if (device.getInstallerId() != null) {
            Installer installer = installerRepository.selectById(device.getInstallerId());
            if (installer != null) {
                result.setInstallerId(installer.getId());
                result.setInstallerCode(installer.getInstallerCode());
            }
        }

        // 11. 计算多级经销商分润（从 device_dealer 表获取链路）
        try {
            calculateMultiLevelDealerCommission(result, deviceId, dealerAmount);
        } catch (Exception e) {
            log.debug("多级经销商分润计算失败（device_dealer表可能不存在）: {}", e.getMessage());
        }

        // 12. 检查双重身份（装机商 + 经销商）
        try {
            checkDualIdentity(result, device);
        } catch (Exception e) {
            log.debug("双重身份检查失败: {}", e.getMessage());
        }

        log.info("分润计算完成: deviceId={}, payAmount={}, profitMode={}, profitAmount={}, installerAmount={}, dealerAmount={}, companyAmount={}",
                deviceId, payAmount, profitMode, profitAmount, installerAmount, dealerAmount, companyAmount);

        return result;
    }

    /**
     * 计算多级经销商分润
     * 从 device_dealer 表获取分销链路，计算每一级的实际所得
     *
     * 分润逻辑：
     * - 设备创建时指定总经销商分润比例（例如30%），形成经销商分润池
     * - commission_rate 表示下级经销商从总经销商分润池中抽取的比例
     * - 上级经销商分润 = 总池子 × (100% - 所有下级的commission_rate之和)
     *
     * 例如：总利润=100，经销商分润比例=30%，经销商分润池=30
     *      B是一级经销商，C是二级经销商(rate=10%)
     *      C分润 = 30 × 10% = 3
     *      B分润 = 30 × (100% - 10%) = 27
     *
     * @param result 分润结果
     * @param deviceId 设备ID
     * @param dealerPoolAmount 经销商分润池总额
     */
    private void calculateMultiLevelDealerCommission(CommissionResult result, String deviceId, BigDecimal dealerPoolAmount) {
        List<DeviceDealer> dealerChain = deviceDealerRepository.getDealerChainByDeviceId(deviceId);
        if (dealerChain == null || dealerChain.isEmpty()) {
            return;
        }

        List<CommissionDetail> dealerDetails = new ArrayList<>();

        // 计算所有下级经销商的分润比例之和
        BigDecimal totalSubRate = BigDecimal.ZERO;
        for (int i = 0; i < dealerChain.size(); i++) {
            DeviceDealer dd = dealerChain.get(i);

            // 跳过一级经销商（最上级），一级经销商没有commission_rate
            if (i == 0) {
                continue;
            }

            BigDecimal rate = dd.getCommissionRate() != null ? dd.getCommissionRate() : BigDecimal.ZERO;
            totalSubRate = totalSubRate.add(rate);
        }

        if (totalSubRate.compareTo(HUNDRED) > 0) {
            log.warn("下级经销商分润比例之和超过100%: deviceId={}, totalSubRate={}", deviceId, totalSubRate);
        }

        // 计算每个经销商的分润
        for (int i = 0; i < dealerChain.size(); i++) {
            DeviceDealer dd = dealerChain.get(i);
            BigDecimal rate = dd.getCommissionRate() != null ? dd.getCommissionRate() : BigDecimal.ZERO;

            // 获取经销商名称
            String dealerName = "经销商" + dd.getDealerCode();
            if (dd.getDealerId() != null) {
                Dealer dealer = dealerRepository.selectById(dd.getDealerId());
                if (dealer != null) {
                    dealerName = dealer.getName();
                }
            }

            BigDecimal actualAmount;

            if (i == 0) {
                // 一级经销商（最上级）：分润 = 总池子 × (100% - 所有下级的rate之和)
                BigDecimal topRate = HUNDRED.subtract(totalSubRate);
                actualAmount = dealerPoolAmount.multiply(topRate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
                rate = topRate; // 显示计算出的比例
            } else {
                // 下级经销商：分润 = 总池子 × 该经销商的rate
                actualAmount = dealerPoolAmount.multiply(rate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            }

            CommissionDetail detail = new CommissionDetail(
                "DEALER",
                dd.getDealerId(),
                dd.getDealerCode(),
                dealerName,
                rate,
                dealerPoolAmount,  // baseAmount 表示总池子
                actualAmount,      // actualAmount 表示该经销商实际所得
                dd.getLevel()
            );
            dealerDetails.add(detail);
        }

        // 将经销商明细加入结果
        result.getDetails().addAll(dealerDetails);

        log.debug("多级经销商分润计算完成: deviceId={}, dealerCount={}, totalSubRate={}%",
                deviceId, dealerChain.size(), totalSubRate);
    }

    /**
     * 检查双重身份（装机商 + 经销商）
     * 如果同一个用户既是装机商又是经销商，合并他们的分润
     */
    private void checkDualIdentity(CommissionResult result, ManufacturedDevice device) {
        if (result.getInstallerId() == null) {
            return;
        }

        // 查找与装机商关联的用户
        QueryWrapper<User> installerUserQw = new QueryWrapper<>();
        installerUserQw.lambda()
                .eq(User::getInstallerId, result.getInstallerId())
                .eq(User::getIsInstaller, 1);
        List<User> installerUsers = userRepository.selectList(installerUserQw);
        if (installerUsers.isEmpty()) {
            return;
        }

        // 检查这些用户是否也是经销商
        for (User installerUser : installerUsers) {
            if (installerUser.getIsDealer() != null && installerUser.getIsDealer() == 1 
                    && installerUser.getDealerId() != null) {
                // 找到双重身份用户，检查这个经销商是否在分润明细中
                Long dualDealerId = installerUser.getDealerId();
                for (CommissionDetail detail : result.getDetails()) {
                    if ("DEALER".equals(detail.getType()) && dualDealerId.equals(detail.getEntityId())) {
                        // 找到了，这个用户既是装机商又是经销商
                        result.setHasDualIdentity(true);
                        result.setDualIdentityUserId(installerUser.getId());
                        // 合并金额 = 装机商分润 + 该经销商分润
                        BigDecimal totalAmount = result.getInstallerActualAmount()
                                .add(detail.getActualAmount() != null ? detail.getActualAmount() : BigDecimal.ZERO);
                        result.setDualIdentityTotalAmount(totalAmount);
                        
                        log.info("检测到双重身份: userId={}, installerId={}, dealerId={}, 合计={}",
                                installerUser.getId(), result.getInstallerId(), dualDealerId, totalAmount);
                        return;
                    }
                }
            }
        }
    }

    /**
     * 根据设备ID获取设备
     */
    private ManufacturedDevice getDeviceByDeviceId(String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return null;
        }
        QueryWrapper<ManufacturedDevice> qw = new QueryWrapper<>();
        qw.lambda().eq(ManufacturedDevice::getDeviceId, deviceId);
        return manufacturedDeviceRepository.selectOne(qw);
    }

    /**
     * 计算手续费
     */
    private BigDecimal calculateFeeAmount(BigDecimal payAmount, PlanCommission commission, String paymentMethod) {
        if (payAmount == null) {
            return BigDecimal.ZERO;
        }

        if (PaymentFeeRuleUtil.supportsMethod(paymentMethod)) {
            return PaymentFeeRuleUtil.calculateFee(payAmount, paymentMethod);
        }

        if (commission == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal feeRate = commission.getFeeRate();
        BigDecimal feeFixed = commission.getFeeFixed();
        CommissionFeeType feeType = commission.getFeeType();
        if (feeType == null) {
            feeType = (feeFixed != null && feeFixed.compareTo(BigDecimal.ZERO) > 0)
                    ? CommissionFeeType.MIXED : CommissionFeeType.FIXED;
        }

        BigDecimal fee = BigDecimal.ZERO;
        if (feeRate != null) {
            fee = payAmount.multiply(feeRate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        }
        if (CommissionFeeType.MIXED == feeType && feeFixed != null) {
            fee = fee.add(feeFixed);
        }

        return fee.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateProfitAmount(BigDecimal payAmount, BigDecimal feeAmount, BigDecimal planCost,
                                             CommissionProfitMode profitMode) {
        if (payAmount == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal distributable = payAmount.subtract(feeAmount != null ? feeAmount : BigDecimal.ZERO);
        if (CommissionProfitMode.REVENUE != profitMode) {
            distributable = distributable.subtract(planCost != null ? planCost : BigDecimal.ZERO);
        }

        return distributable.compareTo(BigDecimal.ZERO) > 0
                ? distributable.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    private BigDecimal resolvePlanCost(String planId, PlanCommission commission) {
        if (commission != null && commission.getPlanCost() != null) {
            return commission.getPlanCost();
        }

        CloudPlan plan = getCloudPlanByPlanId(planId);
        if (plan != null && plan.getPlanCost() != null) {
            return plan.getPlanCost();
        }

        return BigDecimal.ZERO;
    }

    /**
     * 获取套餐分润配置
     */
    private PlanCommission getPlanCommission(String planId) {
        if (planId == null || planId.trim().isEmpty()) {
            return null;
        }
        QueryWrapper<PlanCommission> qw = new QueryWrapper<>();
        qw.lambda().eq(PlanCommission::getPlanId, planId);
        return planCommissionRepository.selectOne(qw);
    }

    private CloudPlan getCloudPlanByPlanId(String planId) {
        if (planId == null || planId.trim().isEmpty()) {
            return null;
        }
        QueryWrapper<CloudPlan> qw = new QueryWrapper<>();
        qw.lambda().eq(CloudPlan::getPlanId, planId);
        CloudPlan plan = cloudPlanRepository.selectOne(qw);
        if (plan != null) {
            return plan;
        }
        try {
            Long id = Long.parseLong(planId);
            return cloudPlanRepository.selectById(id);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 为订单填充分润信息
     * 在支付成功时调用
     * 
     * 字段对应：
     * - 装机商: installer_id, installer_code, installer_rate, installer_amount
     * - 经销商: dealer_id, dealer_code, dealer_rate, dealer_amount
     */
    public void fillOrderCommission(PaymentOrder order, String deviceId) {
        // 获取设备信息
        ManufacturedDevice device = getDeviceByDeviceId(deviceId);
        if (device == null) {
            log.warn("填充分润信息失败，设备不存在: deviceId={}", deviceId);
            return;
        }
        
        // 计算分润
        CommissionResult result = calculateCommission(
                deviceId,
                order.getAmount(),
                order.getProductId(),
                order.getPaymentMethod()
        );
        
        // ========== 财务信息 ==========
        order.setFeeAmount(result.getFeeAmount());
        order.setPlanCost(result.getPlanCost());
        order.setProfitAmount(result.getProfitAmount());
        
        // ========== 装机商信息 ==========
        order.setInstallerId(device.getInstallerId());
        
        // 从 Installer 表获取装机商代码
        if (device.getInstallerId() != null) {
            Installer installer = installerRepository.selectById(device.getInstallerId());
            if (installer != null) {
                order.setInstallerCode(installer.getInstallerCode());
            }
        }
        
        // 装机商分润比例和金额
        BigDecimal installerRate = device.getInstallerCommissionRate();
        order.setInstallerRate(installerRate != null ? installerRate : BigDecimal.ZERO);
        order.setInstallerAmount(result.getInstallerActualAmount() != null 
                ? result.getInstallerActualAmount() : BigDecimal.ZERO);
        
        // ========== 经销商信息 ==========
        order.setDealerId(device.getCurrentDealerId());
        
        // 从 Dealer 表获取经销商代码
        if (device.getCurrentDealerId() != null) {
            Dealer dealer = dealerRepository.selectById(device.getCurrentDealerId());
            if (dealer != null) {
                order.setDealerCode(dealer.getDealerCode());
            }
        }
        
        // 经销商分润比例和金额
        BigDecimal dealerRate = device.getDealerCommissionRate();
        order.setDealerRate(dealerRate != null ? dealerRate : BigDecimal.ZERO);
        order.setDealerAmount(result.getLevel1BaseAmount() != null 
                ? result.getLevel1BaseAmount() : BigDecimal.ZERO);
        
        // ========== 其他信息 ==========
        order.setOnlineCountry(device.getCountry());
        
        log.info("填充分润信息完成: orderId={}, installerId={}, installerCode={}, installerRate={}, installerAmount={}, " +
                "dealerId={}, dealerCode={}, dealerRate={}, dealerAmount={}",
                order.getOrderId(), order.getInstallerId(), order.getInstallerCode(), order.getInstallerRate(), order.getInstallerAmount(),
                order.getDealerId(), order.getDealerCode(), order.getDealerRate(), order.getDealerAmount());
    }
    
    /**
     * 获取分润计算结果
     */
    public CommissionResult getCommissionResult(String deviceId, BigDecimal payAmount, String planId) {
        return calculateCommission(deviceId, payAmount, planId);
    }
}
