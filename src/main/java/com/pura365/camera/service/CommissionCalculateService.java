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
        CommissionResult result = new CommissionResult();
        result.setPayAmount(payAmount);

        // 1. 获取套餐分润配置（手续费、成本等）
        PlanCommission planCommission = getPlanCommission(planId);
        if (planCommission == null) {
            log.warn("未找到套餐分润配置: planId={}", planId);
            return result;
        }

        // 2. 获取设备信息（分润比例存储在设备级别）
        ManufacturedDevice device = getDeviceByDeviceId(deviceId);
        if (device == null) {
            log.warn("未找到设备: deviceId={}", deviceId);
            return result;
        }

        // 3. 计算手续费
        BigDecimal feeAmount = calculateFeeAmount(payAmount, planCommission);
        result.setFeeAmount(feeAmount);

        // 4. 获取套餐成本
        BigDecimal planCost = planCommission.getPlanCost() != null ? planCommission.getPlanCost() : BigDecimal.ZERO;
        result.setPlanCost(planCost);

        // 5. 计算可分润金额 = 支付金额 - 手续费 - 套餐成本
        BigDecimal profitAmount = payAmount.subtract(feeAmount).subtract(planCost);
        if (profitAmount.compareTo(BigDecimal.ZERO) < 0) {
            profitAmount = BigDecimal.ZERO;
        }
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

        log.info("分润计算完成: deviceId={}, payAmount={}, profitAmount={}, installerAmount={}, dealerAmount={}, companyAmount={}",
                deviceId, payAmount, profitAmount, installerAmount, dealerAmount, companyAmount);

        return result;
    }

    /**
     * 计算多级经销商分润
     * 从 device_dealer 表获取分销链路，计算每一级的实际所得
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

        // 从最底层（一级）开始计算
        // 一级经销商的池子 = dealerPoolAmount
        // 二级经销商从一级的池子中抽取
        // 三级经销商从二级的池子中抽取...
        
        BigDecimal currentPool = dealerPoolAmount;
        List<CommissionDetail> dealerDetails = new ArrayList<>();
        
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
            
            // 计算该经销商的分润金额
            BigDecimal baseAmount;
            BigDecimal actualAmount;
            
            if (i == dealerChain.size() - 1) {
                // 最后一个经销商（最底层），获得剩余的所有金额
                baseAmount = currentPool;
                actualAmount = currentPool;
            } else {
                // 不是最底层，需要给下级留出一部分
                // 下一级的分润比例
                DeviceDealer nextDealer = dealerChain.get(i + 1);
                BigDecimal nextRate = nextDealer.getCommissionRate() != null ? nextDealer.getCommissionRate() : BigDecimal.ZERO;
                BigDecimal nextAmount = currentPool.multiply(nextRate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
                
                baseAmount = currentPool;
                actualAmount = currentPool.subtract(nextAmount);
                
                // 下一级的池子变小
                currentPool = nextAmount;
            }
            
            CommissionDetail detail = new CommissionDetail(
                "DEALER",
                dd.getDealerId(),
                dd.getDealerCode(),
                dealerName,
                rate,
                baseAmount,
                actualAmount,
                dd.getLevel()
            );
            dealerDetails.add(detail);
        }
        
        // 将经销商明细加入结果
        result.getDetails().addAll(dealerDetails);
        
        log.debug("多级经销商分润计算完成: deviceId={}, dealerCount={}", deviceId, dealerChain.size());
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
        
        // 经销商分润
        order.setDealerAmount(result.getLevel1BaseAmount());
    }
    
    /**
     * 获取分润计算结果
     */
    public CommissionResult getCommissionResult(String deviceId, BigDecimal payAmount, String planId) {
        return calculateCommission(deviceId, payAmount, planId);
    }
}
