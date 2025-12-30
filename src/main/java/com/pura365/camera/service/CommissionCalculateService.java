package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.domain.*;
import com.pura365.camera.repository.*;
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
 * 多级分润计算服务
 * 
 * 分润计算逻辑：
 * 1. 可分润金额 = 套餐价格 - 手续费 - 套餐成本
 * 2. 装机商分润 = 可分润金额 × 装机商比例（如5%） ← 独立分润池
 * 3. 一级经销商分润 = 可分润金额 × 一级比例（如60%） ← 独立分润池
 * 4. 二级经销商分润 = 一级经销商分润 × 二级抽佣率（如45%）
 * 5. 三级经销商分润 = 二级经销商分润 × 三级抽佣率，以此类推...
 * 6. 公司利润 = 可分润金额 - 装机商分润 - 一级经销商分润
 * 
 * 最终：
 * - 装机商实得 = 装机商分润（独立分润池，不受经销商影响）
 * - 一级经销商实得 = 一级分润 - 二级分润
 * - 二级经销商实得 = 二级分润 - 三级分润
 * 
 * 双重身份处理：
 * - 如果装机商同时是经销商，该用户同时获得装机商分润和经销商分润
 * - 当双重身份用户直接分配给下级时，系统自动插入虚拟一级经销商记录
 * - 确保下级经销商基于经销商分润计算，而不是基于总利润
 */
@Service
public class CommissionCalculateService {

    private static final Logger log = LoggerFactory.getLogger(CommissionCalculateService.class);

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Autowired
    private DeviceVendorRepository deviceVendorRepository;

    @Autowired
    private InstallerRepository installerRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private PlanCommissionRepository planCommissionRepository;

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
        CommissionResult result = new CommissionResult();
        result.setPayAmount(payAmount);

        // 1. 获取套餐分润配置
        PlanCommission planCommission = getPlanCommission(planId);
        if (planCommission == null) {
            log.warn("未找到套餐分润配置: planId={}", planId);
            return result;
        }

        // 2. 计算手续费
        BigDecimal feeAmount = calculateFeeAmount(payAmount, planCommission);
        result.setFeeAmount(feeAmount);

        // 3. 获取套餐成本
        BigDecimal planCost = planCommission.getPlanCost() != null ? planCommission.getPlanCost() : BigDecimal.ZERO;
        result.setPlanCost(planCost);

        // 4. 计算可分润金额 = 支付金额 - 手续费 - 套餐成本
        BigDecimal profitAmount = payAmount.subtract(feeAmount).subtract(planCost);
        if (profitAmount.compareTo(BigDecimal.ZERO) < 0) {
            profitAmount = BigDecimal.ZERO;
        }
        result.setProfitAmount(profitAmount);

        // 5. 获取设备的分销链路
        List<DeviceVendor> vendorChain = deviceVendorRepository.getVendorChainByDeviceId(deviceId);
        // 创建可修改的副本，避免影响原始数据
        List<DeviceVendor> effectiveVendorChain = new ArrayList<>(vendorChain);
        
        // 6. 获取装机商信息
        Installer installer = null;
        BigDecimal installerRate = planCommission.getInstallerRate();
        if (installerRate == null) {
            installerRate = BigDecimal.ZERO;
        }

        if (!effectiveVendorChain.isEmpty()) {
            DeviceVendor firstVendor = effectiveVendorChain.get(0);
            if (firstVendor.getInstallerId() != null) {
                installer = installerRepository.selectById(firstVendor.getInstallerId());
            }
        }
        
        // 6.5 双重身份处理：如果装机商同时是经销商，且该经销商不在分销链路中
        // 则自动在链路开头插入虚拟的一级经销商记录，保证下级经销商基于经销商分润计算
        if (installer != null) {
            User installerUser = findUserByInstallerId(installer.getId());
            if (installerUser != null && installerUser.getIsDealer() != null && installerUser.getIsDealer() == 1) {
                Long dealerId = installerUser.getDealerId();
                if (dealerId != null) {
                    // 检查该经销商是否已在分销链路中
                    boolean alreadyInChain = effectiveVendorChain.stream()
                            .anyMatch(dv -> dealerId.equals(dv.getVendorId()));
                    
                    if (!alreadyInChain && !effectiveVendorChain.isEmpty()) {
                        // 双重身份用户直接分配给下级，需要自动插入虚拟的一级经销商记录
                        BigDecimal level1Rate = planCommission.getLevel1Rate() != null ? planCommission.getLevel1Rate() : BigDecimal.ZERO;
                        Vendor dealer = vendorRepository.selectById(dealerId);
                        
                        // 创建虚拟的一级经销商记录
                        DeviceVendor virtualVendor = new DeviceVendor();
                        virtualVendor.setDeviceId(deviceId);
                        virtualVendor.setInstallerId(installer.getId());
                        virtualVendor.setInstallerCode(installer.getInstallerCode());
                        virtualVendor.setVendorId(dealerId);
                        virtualVendor.setVendorCode(dealer != null ? dealer.getVendorCode() : "");
                        virtualVendor.setCommissionRate(level1Rate);
                        virtualVendor.setLevel(1);
                        
                        // 将虚拟记录插入到链路开头，并将其他记录的level+1
                        effectiveVendorChain.add(0, virtualVendor);
                        for (int i = 1; i < effectiveVendorChain.size(); i++) {
                            DeviceVendor dv = effectiveVendorChain.get(i);
                            dv.setLevel(dv.getLevel() != null ? dv.getLevel() + 1 : 2);
                        }
                        
                        log.info("双重身份自动插入一级经销商: deviceId={}, dealerId={}, dealerCode={}, level1Rate={}",
                                deviceId, dealerId, virtualVendor.getVendorCode(), level1Rate);
                    }
                }
            }
        }

        // 7. 计算装机商基础利润 = 可分润金额 × 装机商比例
        // 装机商独立分润池，实得 = 基础利润
        BigDecimal installerBaseAmount = profitAmount.multiply(installerRate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        result.setInstallerBaseAmount(installerBaseAmount);
        result.setInstallerActualAmount(installerBaseAmount); // 装机商实得就是基础利润

        // 8. 递归计算经销商分润
        // 关键逻辑：一级经销商(level=1)基于总可分利润计算，二级及以下基于上级分润计算
        BigDecimal level1BaseAmount = BigDecimal.ZERO; // 一级经销商分润池
        BigDecimal remainingProfit = BigDecimal.ZERO;  // 用于计算下级分润的基数

        for (DeviceVendor dv : effectiveVendorChain) {
            BigDecimal vendorRate = dv.getCommissionRate() != null ? dv.getCommissionRate() : BigDecimal.ZERO;
            BigDecimal vendorShare;
            
            Integer level = dv.getLevel() != null ? dv.getLevel() : 1;
            if (level == 1) {
                // 一级经销商：直接基于总可分利润计算
                // FF经销商可分利润 = 总可分利润 × 60%
                vendorShare = profitAmount.multiply(vendorRate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
                level1BaseAmount = vendorShare;
                remainingProfit = vendorShare; // 作为下级的计算基数
            } else {
                // 二级及以下经销商：基于上级分润计算
                // BB经销商可分利润 = 一级分润 × 45%
                vendorShare = remainingProfit.multiply(vendorRate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
                remainingProfit = vendorShare; // 下一级基于本级的分润金额计算
            }
            
            // 获取经销商信息
            Vendor vendor = vendorRepository.selectById(dv.getVendorId());
            String vendorName = vendor != null ? vendor.getVendorName() : "";

            CommissionDetail detail = new CommissionDetail(
                    "VENDOR",
                    dv.getVendorId(),
                    dv.getVendorCode(),
                    vendorName,
                    vendorRate,
                    vendorShare,
                    BigDecimal.ZERO, // 实际所得稍后计算
                    level
            );
            result.getDetails().add(detail);
        }
        
        result.setLevel1BaseAmount(level1BaseAmount);

        // 9. 计算公司利润
        // 公司利润 = 总可分利润 - 装机商基础利润 - 一级经销商分润池
        BigDecimal companyAmount = profitAmount.subtract(installerBaseAmount).subtract(level1BaseAmount);
        if (companyAmount.compareTo(BigDecimal.ZERO) < 0) {
            companyAmount = BigDecimal.ZERO;
        }
        result.setCompanyAmount(companyAmount);

        // 10. 计算各级经销商实际所得
        // 经销商实际所得 = 本级分润 - 下级分润
        for (int i = 0; i < result.getDetails().size(); i++) {
            CommissionDetail current = result.getDetails().get(i);
            BigDecimal nextLevelShare = BigDecimal.ZERO;
            if (i + 1 < result.getDetails().size()) {
                nextLevelShare = result.getDetails().get(i + 1).getAmount();
            }
            current.setActualAmount(current.getAmount().subtract(nextLevelShare));
        }

        // 添加装机商明细
        if (installer != null) {
            CommissionDetail installerDetail = new CommissionDetail(
                    "INSTALLER",
                    installer.getId(),
                    installer.getInstallerCode(),
                    installer.getInstallerName(),
                    installerRate,
                    installerBaseAmount,
                    installerBaseAmount, // 装机商实得 = 基础利润（独立分润池）
                    0
            );
            result.getDetails().add(0, installerDetail);
            result.setInstallerId(installer.getId());
            result.setInstallerCode(installer.getInstallerCode());
        }

        // 检查双重身份：装机商和经销商是否为同一用户
        checkAndMergeDualIdentity(result, installer, effectiveVendorChain);

        log.info("分润计算完成: deviceId={}, payAmount={}, profitAmount={}, installerAmount={}, level1Amount={}, companyAmount={}, vendorDetails={}, hasDualIdentity={}",
                deviceId, payAmount, profitAmount, installerBaseAmount, level1BaseAmount, companyAmount, result.getDetails().size(), result.isHasDualIdentity());

        return result;
    }

    /**
     * 检查并合并双重身份分润
     * 如果装机商和某个经销商是同一个用户，则该用户两份都拿
     */
    private void checkAndMergeDualIdentity(CommissionResult result, Installer installer, List<DeviceVendor> vendorChain) {
        if (installer == null || vendorChain.isEmpty()) {
            return;
        }

        // 查找拥有该装机商身份的用户
        QueryWrapper<User> installerUserQw = new QueryWrapper<>();
        installerUserQw.lambda()
                .eq(User::getIsInstaller, 1)
                .eq(User::getInstallerId, installer.getId());
        User installerUser = userRepository.selectOne(installerUserQw);
        
        if (installerUser == null || installerUser.getIsDealer() == null || installerUser.getIsDealer() != 1) {
            return; // 装机商用户不具备经销商身份
        }

        Long dealerId = installerUser.getDealerId();
        if (dealerId == null) {
            return;
        }

        // 检查该用户的经销商ID是否在分润链中
        for (DeviceVendor dv : vendorChain) {
            if (dealerId.equals(dv.getVendorId())) {
                // 找到了！该用户同时是装机商和经销商
                result.setHasDualIdentity(true);
                result.setDualIdentityUserId(installerUser.getId());
                
                // 计算双重身份合计金额 = 装机商实得 + 经销商实得
                BigDecimal installerAmount = result.getInstallerActualAmount() != null ? result.getInstallerActualAmount() : BigDecimal.ZERO;
                BigDecimal vendorAmount = BigDecimal.ZERO;
                
                for (CommissionDetail detail : result.getDetails()) {
                    if ("VENDOR".equals(detail.getType()) && dealerId.equals(detail.getEntityId())) {
                        vendorAmount = detail.getActualAmount() != null ? detail.getActualAmount() : BigDecimal.ZERO;
                        break;
                    }
                }
                
                result.setDualIdentityTotalAmount(installerAmount.add(vendorAmount));
                
                log.info("双重身份分润合并: userId={}, installerId={}, dealerId={}, installerAmount={}, vendorAmount={}, total={}",
                        installerUser.getId(), installer.getId(), dealerId, installerAmount, vendorAmount, result.getDualIdentityTotalAmount());
                break;
            }
        }
    }

    /**
     * 根据装机商ID查找用户
     */
    private User findUserByInstallerId(Long installerId) {
        if (installerId == null) {
            return null;
        }
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.lambda()
                .eq(User::getIsInstaller, 1)
                .eq(User::getInstallerId, installerId);
        return userRepository.selectOne(qw);
    }

    /**
     * 计算手续费
     */
    private BigDecimal calculateFeeAmount(BigDecimal payAmount, PlanCommission commission) {
        if (payAmount == null || commission == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal feeRate = commission.getFeeRate();
        BigDecimal feeFixed = commission.getFeeFixed();

        BigDecimal fee = BigDecimal.ZERO;
        if (feeRate != null) {
            fee = payAmount.multiply(feeRate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        }
        if (feeFixed != null) {
            fee = fee.add(feeFixed);
        }

        return fee.setScale(2, RoundingMode.HALF_UP);
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

    /**
     * 为订单填充分润信息
     * 在支付成功时调用
     */
    public void fillOrderCommission(PaymentOrder order, String deviceId) {
        CommissionResult result = calculateCommission(deviceId, order.getAmount(), order.getProductId());
        
        order.setFeeAmount(result.getFeeAmount());
        order.setPlanCost(result.getPlanCost());
        order.setProfitAmount(result.getProfitAmount());
        
        // 装机商信息
        order.setInstallerId(result.getInstallerId());
        order.setInstallerCode(result.getInstallerCode());
        order.setInstallerAmount(result.getInstallerActualAmount());
        order.setInstallerRate(result.getInstallerBaseAmount() != null && result.getProfitAmount() != null 
                && result.getProfitAmount().compareTo(BigDecimal.ZERO) > 0
                ? result.getInstallerBaseAmount().multiply(HUNDRED).divide(result.getProfitAmount(), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);

        // 设置经销商分润（取最终经销商，即最高层级的）
        if (!result.getDetails().isEmpty()) {
            for (int i = result.getDetails().size() - 1; i >= 0; i--) {
                CommissionDetail detail = result.getDetails().get(i);
                if ("VENDOR".equals(detail.getType())) {
                    order.setDealerAmount(detail.getActualAmount());
                    order.setDealerRate(detail.getRate());
                    order.setDealerId(detail.getEntityId());
                    order.setDealerCode(detail.getEntityCode());
                    // 兼容旧字段
                    order.setVendorAmount(detail.getActualAmount());
                    break;
                }
            }
        }
        
        // 双重身份处理：如果装机商和经销商是同一人，两份都拿
        // 订单中分别记录装机商分润和经销商分润，不合并
        // 结算时根据 hasDualIdentity 和 dualIdentityUserId 合并给同一用户
        if (result.isHasDualIdentity()) {
            log.info("订单存在双重身份分润: orderId={}, userId={}, installerAmount={}, dealerAmount={}, total={}",
                    order.getOrderId(), result.getDualIdentityUserId(), 
                    order.getInstallerAmount(), order.getDealerAmount(), result.getDualIdentityTotalAmount());
        }
    }
    
    /**
     * 获取分润计算结果（供外部查询双重身份信息）
     */
    public CommissionResult getCommissionResult(String deviceId, BigDecimal payAmount, String planId) {
        return calculateCommission(deviceId, payAmount, planId);
    }
}
