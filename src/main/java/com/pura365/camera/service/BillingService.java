package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.domain.*;
import com.pura365.camera.enums.PaymentOrderStatus;
import com.pura365.camera.repository.*;
import com.pura365.camera.util.PaymentFeeRuleUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 账单统计服务
 * 提供经销商/业务员维度的账单统计和导出功能
 */
@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);
    private static final String DEFAULT_CURRENCY = "CNY";

    @Autowired
    private PaymentOrderRepository orderRepository;

@Autowired
    private DealerRepository dealerRepository;

    @Autowired
    private InstallerRepository installerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ManufacturedDeviceRepository deviceRepository;

    @Autowired
    private CloudPlanRepository cloudPlanRepository;

    @Autowired
    private DeviceDealerRepository deviceDealerRepository;

    private BigDecimal safeAmount(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal calculateEffectiveFeeAmount(PaymentOrder order) {
        if (order == null) {
            return BigDecimal.ZERO;
        }

        if (PaymentFeeRuleUtil.supportsMethod(order.getPaymentMethod())) {
            return PaymentFeeRuleUtil.calculateFee(order.getAmount(), order.getPaymentMethod());
        }

        return safeAmount(order.getFeeAmount()).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateEffectiveProfitAmount(PaymentOrder order, BigDecimal effectiveFeeAmount) {
        if (order == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal safeEffectiveFee = safeAmount(effectiveFeeAmount);
        BigDecimal snapshotProfit = order.getProfitAmount();
        BigDecimal snapshotFee = safeAmount(order.getFeeAmount());

        if (snapshotProfit != null) {
            BigDecimal adjusted = snapshotProfit.subtract(safeEffectiveFee.subtract(snapshotFee));
            return adjusted.compareTo(BigDecimal.ZERO) > 0
                    ? adjusted.setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
        }

        BigDecimal fallback = safeAmount(order.getAmount())
                .subtract(safeEffectiveFee)
                .subtract(safeAmount(order.getPlanCost()));
        return fallback.compareTo(BigDecimal.ZERO) > 0
                ? fallback.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }

    private void normalizeFinancialFieldsForDisplay(PaymentOrder order) {
        if (order == null || PaymentOrderStatus.PAID != order.getStatus()) {
            return;
        }

        BigDecimal effectiveFee = calculateEffectiveFeeAmount(order);
        BigDecimal effectiveProfit = calculateEffectiveProfitAmount(order, effectiveFee);
        order.setFeeAmount(effectiveFee);
        order.setProfitAmount(effectiveProfit);
    }

    private BigDecimal calculateRemainingProfit(BigDecimal totalProfitAmount,
                                                BigDecimal totalInstallerAmount,
                                                BigDecimal totalDealerAmount,
                                                String dimension) {
        BigDecimal safeProfitAmount = totalProfitAmount != null ? totalProfitAmount : BigDecimal.ZERO;
        BigDecimal safeInstallerAmount = totalInstallerAmount != null ? totalInstallerAmount : BigDecimal.ZERO;
        BigDecimal safeDealerAmount = totalDealerAmount != null ? totalDealerAmount : BigDecimal.ZERO;
        BigDecimal remainingProfit = safeProfitAmount.subtract(safeInstallerAmount).subtract(safeDealerAmount);
        if (remainingProfit.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("Remaining profit is negative, clamp to zero. dimension={}, totalProfitAmount={}, totalInstallerAmount={}, totalDealerAmount={}, rawRemainingProfit={}",
                    dimension, safeProfitAmount, safeInstallerAmount, safeDealerAmount, remainingProfit);
            return BigDecimal.ZERO;
        }
        return remainingProfit;
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.trim().isEmpty()) {
            return DEFAULT_CURRENCY;
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private void addCurrencyAmount(Map<String, BigDecimal> currencyMap, String currency, BigDecimal amount) {
        String normalizedCurrency = normalizeCurrency(currency);
        currencyMap.merge(normalizedCurrency, safeAmount(amount), BigDecimal::add);
    }

    @SuppressWarnings("unchecked")
    private Map<String, BigDecimal> getOrCreateCurrencyMap(Map<String, Object> target, String key) {
        Object existed = target.get(key);
        if (existed instanceof Map<?, ?>) {
            return (Map<String, BigDecimal>) existed;
        }
        Map<String, BigDecimal> currencyMap = new HashMap<>();
        target.put(key, currencyMap);
        return currencyMap;
    }

    private List<String> sortCurrencies(Set<String> currencySet) {
        List<String> sorted = new ArrayList<>();
        if (currencySet.contains("CNY")) {
            sorted.add("CNY");
        }
        if (currencySet.contains("USD")) {
            sorted.add("USD");
        }

        List<String> others = new ArrayList<>();
        for (String currency : currencySet) {
            if (!"CNY".equals(currency) && !"USD".equals(currency)) {
                others.add(currency);
            }
        }
        Collections.sort(others);
        sorted.addAll(others);
        return sorted;
    }

    private BigDecimal getCurrencyAmount(Map<String, BigDecimal> currencyMap, String currency) {
        if (currencyMap == null) {
            return BigDecimal.ZERO;
        }
        return currencyMap.getOrDefault(currency, BigDecimal.ZERO);
    }

    private Map<String, BigDecimal> toOrderedCurrencyAmountMap(Map<String, BigDecimal> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashMap<String, BigDecimal> ordered = new LinkedHashMap<>();
        for (String currency : sortCurrencies(source.keySet())) {
            ordered.put(currency, source.get(currency));
        }
        return ordered;
    }

    private List<Map<String, Object>> buildCurrencySummaryStats(Map<String, BigDecimal> totalRevenueByCurrency,
                                                                Map<String, BigDecimal> totalCostByCurrency,
                                                                Map<String, BigDecimal> totalProfitAmountByCurrency,
                                                                Map<String, BigDecimal> totalInstallerAmountByCurrency,
                                                                Map<String, BigDecimal> totalDealerAmountByCurrency,
                                                                Map<String, BigDecimal> totalSettledAmountByCurrency,
                                                                Map<String, BigDecimal> totalUnsettledAmountByCurrency,
                                                                String dimension) {
        Set<String> currencies = new HashSet<>();
        if (totalRevenueByCurrency != null) {
            currencies.addAll(totalRevenueByCurrency.keySet());
        }
        if (totalCostByCurrency != null) {
            currencies.addAll(totalCostByCurrency.keySet());
        }
        if (totalProfitAmountByCurrency != null) {
            currencies.addAll(totalProfitAmountByCurrency.keySet());
        }
        if (totalInstallerAmountByCurrency != null) {
            currencies.addAll(totalInstallerAmountByCurrency.keySet());
        }
        if (totalDealerAmountByCurrency != null) {
            currencies.addAll(totalDealerAmountByCurrency.keySet());
        }
        if (totalSettledAmountByCurrency != null) {
            currencies.addAll(totalSettledAmountByCurrency.keySet());
        }
        if (totalUnsettledAmountByCurrency != null) {
            currencies.addAll(totalUnsettledAmountByCurrency.keySet());
        }

        List<Map<String, Object>> stats = new ArrayList<>();
        for (String currency : sortCurrencies(currencies)) {
            BigDecimal totalProfit = getCurrencyAmount(totalProfitAmountByCurrency, currency);
            BigDecimal totalInstaller = getCurrencyAmount(totalInstallerAmountByCurrency, currency);
            BigDecimal totalDealer = getCurrencyAmount(totalDealerAmountByCurrency, currency);
            BigDecimal totalRemaining = calculateRemainingProfit(totalProfit, totalInstaller, totalDealer, dimension + "-" + currency);

            Map<String, Object> stat = new HashMap<>();
            stat.put("currency", currency);
            stat.put("totalRevenue", getCurrencyAmount(totalRevenueByCurrency, currency));
            stat.put("totalCost", getCurrencyAmount(totalCostByCurrency, currency));
            stat.put("totalProfitAmount", totalProfit);
            stat.put("totalInstallerAmount", totalInstaller);
            stat.put("totalDealerAmount", totalDealer);
            stat.put("totalRemainingProfit", totalRemaining);
            stat.put("totalSettledAmount", getCurrencyAmount(totalSettledAmountByCurrency, currency));
            stat.put("totalUnsettledAmount", getCurrencyAmount(totalUnsettledAmountByCurrency, currency));
            stats.add(stat);
        }
        return stats;
    }

    private static class BillingDataScope {
        private String installerCode;
        private Long dealerId;
        private boolean useInstallerDealerOr;
        private boolean showInstallerInfo = true;
        private boolean showDealerInfo = true;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private BillingDataScope resolveBillingDataScope(Long currentUserId, String installerCode, Long dealerId) {
        return resolveBillingDataScope(currentUserId, installerCode, dealerId, null);
    }

    private String normalizeDimension(String dimension) {
        if (!hasText(dimension)) {
            return null;
        }
        String normalized = dimension.trim().toLowerCase(Locale.ROOT);
        if ("installer".equals(normalized) || "dealer".equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private BillingDataScope resolveBillingDataScope(Long currentUserId, String installerCode, Long dealerId, String dimension) {
        BillingDataScope scope = new BillingDataScope();
        scope.installerCode = installerCode;
        scope.dealerId = dealerId;
        String normalizedDimension = normalizeDimension(dimension);

        if ("installer".equals(normalizedDimension)) {
            scope.showDealerInfo = false;
        } else if ("dealer".equals(normalizedDimension)) {
            scope.showInstallerInfo = false;
        }

        if (currentUserId == null) {
            return scope;
        }

        User currentUser = userRepository.selectById(currentUserId);
        if (currentUser == null) {
            return scope;
        }

        boolean isAdmin = currentUser.getRole() != null && currentUser.getRole() == 3;
        if (isAdmin) {
            return scope;
        }

        boolean isInstaller = currentUser.getIsInstaller() != null
                && currentUser.getIsInstaller() == 1
                && currentUser.getInstallerId() != null;
        boolean isDealer = currentUser.getIsDealer() != null
                && currentUser.getIsDealer() == 1
                && currentUser.getDealerId() != null;

        scope.showInstallerInfo = isInstaller;
        scope.showDealerInfo = isDealer;

        if (isInstaller) {
            scope.installerCode = null;
            Installer installer = installerRepository.selectById(currentUser.getInstallerId());
            if (installer != null && hasText(installer.getInstallerCode())) {
                scope.installerCode = installer.getInstallerCode();
            }
        }
        if (isDealer) {
            scope.dealerId = currentUser.getDealerId();
        }

        if ("installer".equals(normalizedDimension) && isInstaller) {
            scope.dealerId = null;
            scope.showDealerInfo = false;
        } else if ("dealer".equals(normalizedDimension) && isDealer) {
            scope.installerCode = null;
            scope.showInstallerInfo = false;
        }

        scope.useInstallerDealerOr = isInstaller && isDealer && hasText(scope.installerCode) && scope.dealerId != null;
        if ("installer".equals(normalizedDimension) || "dealer".equals(normalizedDimension)) {
            scope.useInstallerDealerOr = false;
        }
        return scope;
    }

    private void maskOrderCommissionFields(List<PaymentOrder> orders, boolean showInstallerInfo, boolean showDealerInfo) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        for (PaymentOrder order : orders) {
            if (!showInstallerInfo) {
                order.setInstallerRate(null);
                order.setInstallerAmount(null);
            }
            if (!showDealerInfo) {
                order.setCommissionRate(null);
                order.setDealerRate(null);
                order.setDealerAmount(null);
            }
        }
    }

    private void applyInstallerDealerScope(QueryWrapper<PaymentOrder> qw, BillingDataScope scope) {
        if (scope.useInstallerDealerOr) {
            final String installerCode = scope.installerCode;
            final Long dealerId = scope.dealerId;
            qw.and(wrapper -> wrapper
                    .eq("installer_code", installerCode)
                    .or()
                    .eq("dealer_id", dealerId)
            );
            return;
        }

        if (hasText(scope.installerCode)) {
            qw.lambda().eq(PaymentOrder::getInstallerCode, scope.installerCode);
        }
        if (scope.dealerId != null) {
            qw.lambda().eq(PaymentOrder::getDealerId, scope.dealerId);
        }
    }


    /**
     * 获取装机商账单汇总统计
     * 直接用 installerCode 查询 payment_order 表
     */
    public Map<String, Object> getInstallerBillingSummary(Long currentUserId, Long installerId, String installerCode, Date startDate, Date endDate) {
        // 确定有效的装机商代码
        String effectiveInstallerCode = installerCode;
        
        // 非管理员只能查看自己的数据
        if (currentUserId != null) {
            User currentUser = userRepository.selectById(currentUserId);
            if (currentUser != null) {
                boolean isAdmin = currentUser.getRole() != null && currentUser.getRole() == 3;
                if (!isAdmin && currentUser.getIsInstaller() != null && currentUser.getIsInstaller() == 1 && currentUser.getInstallerId() != null) {
                    Installer installer = installerRepository.selectById(currentUser.getInstallerId());
                    if (installer != null) {
                        effectiveInstallerCode = installer.getInstallerCode();
                    }
                }
            }
        }
        
        // 构建查询条件
        QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();
        qw.lambda().eq(PaymentOrder::getStatus, PaymentOrderStatus.PAID);
        
        // 用 installerCode 查询
        if (effectiveInstallerCode != null && !effectiveInstallerCode.trim().isEmpty()) {
            qw.lambda().eq(PaymentOrder::getInstallerCode, effectiveInstallerCode);
        }
        if (startDate != null) {
            qw.lambda().ge(PaymentOrder::getPaidAt, startDate);
        }
        if (endDate != null) {
            qw.lambda().le(PaymentOrder::getPaidAt, endDate);
        }

        List<PaymentOrder> orders = orderRepository.selectList(qw);

        // 按装机商代码分组统计
        Map<String, Map<String, Object>> installerStats = new HashMap<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalProfitAmount = BigDecimal.ZERO;
        BigDecimal totalInstallerAmount = BigDecimal.ZERO;
        BigDecimal totalDealerAmount = BigDecimal.ZERO;
        BigDecimal totalSettledInstallerAmount = BigDecimal.ZERO;
        BigDecimal totalUnsettledInstallerAmount = BigDecimal.ZERO;
        Map<String, BigDecimal> totalAmountByCurrency = new HashMap<>();
        Map<String, BigDecimal> totalCostByCurrency = new HashMap<>();
        Map<String, BigDecimal> totalProfitAmountByCurrency = new HashMap<>();
        Map<String, BigDecimal> totalInstallerAmountByCurrency = new HashMap<>();
        Map<String, BigDecimal> totalDealerAmountByCurrency = new HashMap<>();
        Map<String, BigDecimal> totalSettledInstallerAmountByCurrency = new HashMap<>();
        Map<String, BigDecimal> totalUnsettledInstallerAmountByCurrency = new HashMap<>();
        Set<String> deviceIds = new HashSet<>();
        int totalOrders = 0;

        for (PaymentOrder order : orders) {
            String code = order.getInstallerCode();
            if (code == null || code.trim().isEmpty()) {
                code = "未分配";
            }

            final String finalCode = code;
            Map<String, Object> stat = installerStats.computeIfAbsent(code, k -> {
                Map<String, Object> s = new HashMap<>();
                s.put("installerId", order.getInstallerId());
                s.put("installerCode", finalCode);
                s.put("orderCount", 0);
                s.put("totalAmount", BigDecimal.ZERO);
                s.put("installerAmount", BigDecimal.ZERO);
                s.put("settledAmount", BigDecimal.ZERO);
                s.put("unsettledAmount", BigDecimal.ZERO);
                s.put("totalAmountByCurrency", new HashMap<String, BigDecimal>());
                s.put("installerAmountByCurrency", new HashMap<String, BigDecimal>());
                s.put("settledAmountByCurrency", new HashMap<String, BigDecimal>());
                s.put("unsettledAmountByCurrency", new HashMap<String, BigDecimal>());
                return s;
            });

            String currency = normalizeCurrency(order.getCurrency());
            BigDecimal orderAmount = safeAmount(order.getAmount());
            BigDecimal insAmt = order.getInstallerAmount() != null ? order.getInstallerAmount() : BigDecimal.ZERO;
            BigDecimal dealerAmt = order.getDealerAmount() != null ? order.getDealerAmount() : BigDecimal.ZERO;
            BigDecimal planCost = safeAmount(order.getPlanCost());
            boolean isSettled = order.getIsSettled() != null && order.getIsSettled() == 1;

            stat.put("orderCount", (Integer) stat.get("orderCount") + 1);
            stat.put("totalAmount", ((BigDecimal) stat.get("totalAmount")).add(orderAmount));
            stat.put("installerAmount", ((BigDecimal) stat.get("installerAmount")).add(insAmt));
            addCurrencyAmount(getOrCreateCurrencyMap(stat, "totalAmountByCurrency"), currency, orderAmount);
            addCurrencyAmount(getOrCreateCurrencyMap(stat, "installerAmountByCurrency"), currency, insAmt);
            if (isSettled) {
                stat.put("settledAmount", ((BigDecimal) stat.get("settledAmount")).add(insAmt));
                addCurrencyAmount(getOrCreateCurrencyMap(stat, "settledAmountByCurrency"), currency, insAmt);
                totalSettledInstallerAmount = totalSettledInstallerAmount.add(insAmt);
                addCurrencyAmount(totalSettledInstallerAmountByCurrency, currency, insAmt);
            } else {
                stat.put("unsettledAmount", ((BigDecimal) stat.get("unsettledAmount")).add(insAmt));
                addCurrencyAmount(getOrCreateCurrencyMap(stat, "unsettledAmountByCurrency"), currency, insAmt);
                totalUnsettledInstallerAmount = totalUnsettledInstallerAmount.add(insAmt);
                addCurrencyAmount(totalUnsettledInstallerAmountByCurrency, currency, insAmt);
            }

            totalAmount = totalAmount.add(orderAmount);
            totalCost = totalCost.add(planCost);
            BigDecimal effectiveFeeAmount = calculateEffectiveFeeAmount(order);
            BigDecimal effectiveProfitAmount = calculateEffectiveProfitAmount(order, effectiveFeeAmount);
            totalProfitAmount = totalProfitAmount.add(effectiveProfitAmount);
            totalInstallerAmount = totalInstallerAmount.add(insAmt);
            totalDealerAmount = totalDealerAmount.add(dealerAmt);
            addCurrencyAmount(totalAmountByCurrency, currency, orderAmount);
            addCurrencyAmount(totalCostByCurrency, currency, planCost);
            addCurrencyAmount(totalProfitAmountByCurrency, currency, effectiveProfitAmount);
            addCurrencyAmount(totalInstallerAmountByCurrency, currency, insAmt);
            addCurrencyAmount(totalDealerAmountByCurrency, currency, dealerAmt);
            if (order.getDeviceId() != null) {
                deviceIds.add(order.getDeviceId());
            }
            totalOrders++;
        }

        // 补充装机商名称
        List<Map<String, Object>> installerList = new ArrayList<>(installerStats.values());
        for (Map<String, Object> stat : installerList) {
            String code = (String) stat.get("installerCode");
            Long iid = (Long) stat.get("installerId");
            
            String installerName = null;
            if (iid != null) {
                Installer installer = installerRepository.selectById(iid);
                if (installer != null) {
                    installerName = installer.getInstallerName();
                    if (stat.get("installerCode") == null || ((String) stat.get("installerCode")).isEmpty()) {
                        stat.put("installerCode", installer.getInstallerCode());
                    }
                }
            }
            if (installerName == null && code != null && !"未分配".equals(code)) {
                QueryWrapper<Installer> iqw = new QueryWrapper<>();
                iqw.lambda().eq(Installer::getInstallerCode, code);
                Installer installer = installerRepository.selectOne(iqw);
                if (installer != null) {
                    installerName = installer.getInstallerName();
                    stat.put("installerId", installer.getId());
                }
            }
            stat.put("installerName", installerName != null ? installerName : ("未分配".equals(code) ? "未分配" : "未知"));
        }

        // 计算剩余利润 = 可分润金额 - 装机商分润 - 经销商分润
        for (Map<String, Object> stat : installerList) {
            stat.put("totalAmountByCurrency", toOrderedCurrencyAmountMap(getOrCreateCurrencyMap(stat, "totalAmountByCurrency")));
            stat.put("installerAmountByCurrency", toOrderedCurrencyAmountMap(getOrCreateCurrencyMap(stat, "installerAmountByCurrency")));
            stat.put("settledAmountByCurrency", toOrderedCurrencyAmountMap(getOrCreateCurrencyMap(stat, "settledAmountByCurrency")));
            stat.put("unsettledAmountByCurrency", toOrderedCurrencyAmountMap(getOrCreateCurrencyMap(stat, "unsettledAmountByCurrency")));
        }

        BigDecimal totalRemainingProfit = calculateRemainingProfit(
                totalProfitAmount, totalInstallerAmount, totalDealerAmount, "installer");

        List<Map<String, Object>> currencyStats = buildCurrencySummaryStats(
                totalAmountByCurrency,
                totalCostByCurrency,
                totalProfitAmountByCurrency,
                totalInstallerAmountByCurrency,
                totalDealerAmountByCurrency,
                totalSettledInstallerAmountByCurrency,
                totalUnsettledInstallerAmountByCurrency,
                "installer");

        Map<String, BigDecimal> totalRemainingProfitByCurrency = new HashMap<>();
        for (Map<String, Object> currencyStat : currencyStats) {
            String currency = (String) currencyStat.get("currency");
            BigDecimal remaining = (BigDecimal) currencyStat.get("totalRemainingProfit");
            totalRemainingProfitByCurrency.put(currency, remaining);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", installerList);
        result.put("totalOrders", totalOrders);
        result.put("totalDevices", deviceIds.size());
        result.put("totalRevenue", totalAmount);
        result.put("totalAmount", totalAmount);
        result.put("totalCost", totalCost);
        result.put("totalProfitAmount", totalProfitAmount);
        result.put("totalInstallerAmount", totalInstallerAmount);
        result.put("totalDealerAmount", totalDealerAmount);
        result.put("totalRemainingProfit", totalRemainingProfit);
        result.put("totalSettledInstallerAmount", totalSettledInstallerAmount);
        result.put("totalUnsettledInstallerAmount", totalUnsettledInstallerAmount);
        result.put("currencyStats", currencyStats);
        result.put("totalRevenueByCurrency", toOrderedCurrencyAmountMap(totalAmountByCurrency));
        result.put("totalAmountByCurrency", toOrderedCurrencyAmountMap(totalAmountByCurrency));
        result.put("totalCostByCurrency", toOrderedCurrencyAmountMap(totalCostByCurrency));
        result.put("totalProfitAmountByCurrency", toOrderedCurrencyAmountMap(totalProfitAmountByCurrency));
        result.put("totalInstallerAmountByCurrency", toOrderedCurrencyAmountMap(totalInstallerAmountByCurrency));
        result.put("totalDealerAmountByCurrency", toOrderedCurrencyAmountMap(totalDealerAmountByCurrency));
        result.put("totalRemainingProfitByCurrency", toOrderedCurrencyAmountMap(totalRemainingProfitByCurrency));
        result.put("totalSettledAmountByCurrency", toOrderedCurrencyAmountMap(totalSettledInstallerAmountByCurrency));
        result.put("totalUnsettledAmountByCurrency", toOrderedCurrencyAmountMap(totalUnsettledInstallerAmountByCurrency));
        return result;
    }

    /**
     * 获取经销商账单汇总统计
     * 根据当前用户角色自动过滤
     */
    public Map<String, Object> getDealerBillingSummary(Long currentUserId, String installerCode, Long dealerId, Date startDate, Date endDate) {
        // 根据当前用户角色确定过滤条件
        String effectiveInstallerCode = installerCode;
        Long effectiveDealerId = dealerId;

        if (currentUserId != null) {
            User currentUser = userRepository.selectById(currentUserId);
            if (currentUser != null) {
                boolean isAdmin = currentUser.getRole() != null && currentUser.getRole() == 3;
                if (!isAdmin) {
                    // 装机商只能查看自己的数据
                    if (currentUser.getIsInstaller() != null && currentUser.getIsInstaller() == 1 && currentUser.getInstallerId() != null) {
                        Installer installer = installerRepository.selectById(currentUser.getInstallerId());
                        if (installer != null) {
                            effectiveInstallerCode = installer.getInstallerCode();
                        }
                    }
                    // 经销商只能查看自己的数据
                    if (currentUser.getIsDealer() != null && currentUser.getIsDealer() == 1 && currentUser.getDealerId() != null) {
                        effectiveDealerId = currentUser.getDealerId();
                    }
                }
            }
        }

        // 查询订单：如果指定了经销商，需要查询该经销商相关的所有设备
        List<PaymentOrder> orders;
        if (effectiveDealerId != null) {
            // 查询该经销商在 device_dealer 表中关联的所有设备ID
            List<String> dealerDeviceIds = new ArrayList<>();
            try {
                Dealer dealer = dealerRepository.selectById(effectiveDealerId);
                if (dealer != null && dealer.getDealerCode() != null) {
                    dealerDeviceIds = deviceDealerRepository.listDeviceIdsByDealerCode(dealer.getDealerCode());
                }
            } catch (Exception e) {
                log.debug("查询经销商设备列表失败: {}", e.getMessage());
            }

            // 创建 final 副本供 lambda 使用
            final Long finalEffectiveDealerId = effectiveDealerId;
            final List<String> finalDealerDeviceIds = dealerDeviceIds;

            QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();
            qw.lambda().eq(PaymentOrder::getStatus, PaymentOrderStatus.PAID);
            if (effectiveInstallerCode != null && !effectiveInstallerCode.trim().isEmpty()) {
                qw.lambda().eq(PaymentOrder::getInstallerCode, effectiveInstallerCode);
            }

            // 查询条件：dealer_id = effectiveDealerId OR device_id IN (dealerDeviceIds)
            if (!finalDealerDeviceIds.isEmpty()) {
                qw.and(wrapper -> wrapper
                    .eq("dealer_id", finalEffectiveDealerId)
                    .or()
                    .in("device_id", finalDealerDeviceIds)
                );
            } else {
                qw.lambda().eq(PaymentOrder::getDealerId, effectiveDealerId);
            }

            if (startDate != null) {
                qw.lambda().ge(PaymentOrder::getPaidAt, startDate);
            }
            if (endDate != null) {
                qw.lambda().le(PaymentOrder::getPaidAt, endDate);
            }

            orders = orderRepository.selectList(qw);
        } else {
            // 没有指定经销商，按原逻辑查询
            QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();
            qw.lambda().eq(PaymentOrder::getStatus, PaymentOrderStatus.PAID);
            if (effectiveInstallerCode != null && !effectiveInstallerCode.trim().isEmpty()) {
                qw.lambda().eq(PaymentOrder::getInstallerCode, effectiveInstallerCode);
            }
            if (startDate != null) {
                qw.lambda().ge(PaymentOrder::getPaidAt, startDate);
            }
            if (endDate != null) {
                qw.lambda().le(PaymentOrder::getPaidAt, endDate);
            }

            orders = orderRepository.selectList(qw);
        }

        // 按经销商分组统计
        Map<String, Map<String, Object>> dealerStats = new HashMap<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalProfitAmount = BigDecimal.ZERO;
        BigDecimal totalInstallerAmount = BigDecimal.ZERO;
        BigDecimal totalDealerAmount = BigDecimal.ZERO;
        BigDecimal totalSettledDealerAmount = BigDecimal.ZERO;
        BigDecimal totalUnsettledDealerAmount = BigDecimal.ZERO;
        Map<String, BigDecimal> totalAmountByCurrency = new HashMap<>();
        Map<String, BigDecimal> totalCostByCurrency = new HashMap<>();
        Map<String, BigDecimal> totalProfitAmountByCurrency = new HashMap<>();
        Map<String, BigDecimal> totalInstallerAmountByCurrency = new HashMap<>();
        Map<String, BigDecimal> totalDealerAmountByCurrency = new HashMap<>();
        Map<String, BigDecimal> totalSettledDealerAmountByCurrency = new HashMap<>();
        Map<String, BigDecimal> totalUnsettledDealerAmountByCurrency = new HashMap<>();
        Set<String> deviceIds = new HashSet<>();
        int totalOrders = 0;

        for (PaymentOrder order : orders) {
            Long did = order.getDealerId();
            String key = did != null ? String.valueOf(did) : "未分配";
            String iCode = order.getInstallerCode();

            final Long finalDid = did;
            final String finalICode = iCode;

            // 如果指定了经销商过滤，只统计该经销商的数据
            boolean shouldCountThisDealer = (effectiveDealerId == null || (did != null && did.equals(effectiveDealerId)));

            if (shouldCountThisDealer) {
                Map<String, Object> stat = dealerStats.computeIfAbsent(key, k -> {
                    Map<String, Object> s = new HashMap<>();
                    s.put("dealerId", finalDid);
                    s.put("dealerCode", null);
                    s.put("dealerName", null);
                    s.put("installerCode", finalICode);
                    s.put("orderCount", 0);
                    s.put("totalAmount", BigDecimal.ZERO);
                    s.put("dealerAmount", BigDecimal.ZERO);
                    s.put("settledAmount", BigDecimal.ZERO);
                    s.put("unsettledAmount", BigDecimal.ZERO);
                    s.put("totalAmountByCurrency", new HashMap<String, BigDecimal>());
                    s.put("dealerAmountByCurrency", new HashMap<String, BigDecimal>());
                    s.put("settledAmountByCurrency", new HashMap<String, BigDecimal>());
                    s.put("unsettledAmountByCurrency", new HashMap<String, BigDecimal>());
                    return s;
                });

                String currency = normalizeCurrency(order.getCurrency());
                BigDecimal orderAmount = safeAmount(order.getAmount());
                BigDecimal dAmt = order.getDealerAmount() != null ? order.getDealerAmount() : BigDecimal.ZERO;
                BigDecimal installerAmt = order.getInstallerAmount() != null ? order.getInstallerAmount() : BigDecimal.ZERO;
                BigDecimal planCost = safeAmount(order.getPlanCost());
                boolean isSettled = order.getIsSettled() != null && order.getIsSettled() == 1;

                stat.put("orderCount", (Integer) stat.get("orderCount") + 1);
                stat.put("totalAmount", ((BigDecimal) stat.get("totalAmount")).add(orderAmount));
                stat.put("dealerAmount", ((BigDecimal) stat.get("dealerAmount")).add(dAmt));
                addCurrencyAmount(getOrCreateCurrencyMap(stat, "totalAmountByCurrency"), currency, orderAmount);
                addCurrencyAmount(getOrCreateCurrencyMap(stat, "dealerAmountByCurrency"), currency, dAmt);
                if (isSettled) {
                    stat.put("settledAmount", ((BigDecimal) stat.get("settledAmount")).add(dAmt));
                    addCurrencyAmount(getOrCreateCurrencyMap(stat, "settledAmountByCurrency"), currency, dAmt);
                    totalSettledDealerAmount = totalSettledDealerAmount.add(dAmt);
                    addCurrencyAmount(totalSettledDealerAmountByCurrency, currency, dAmt);
                } else {
                    stat.put("unsettledAmount", ((BigDecimal) stat.get("unsettledAmount")).add(dAmt));
                    addCurrencyAmount(getOrCreateCurrencyMap(stat, "unsettledAmountByCurrency"), currency, dAmt);
                    totalUnsettledDealerAmount = totalUnsettledDealerAmount.add(dAmt);
                    addCurrencyAmount(totalUnsettledDealerAmountByCurrency, currency, dAmt);
                }
            }

            // ========== 处理多级经销商分润 ==========
            // 查询该设备的经销商链路，为上级经销商也统计分润
            if (order.getDeviceId() != null && order.getDealerAmount() != null && order.getDealerAmount().compareTo(BigDecimal.ZERO) > 0) {
                try {
                    List<DeviceDealer> dealerChain = deviceDealerRepository.getDealerChainByDeviceId(order.getDeviceId());
                    if (dealerChain != null && dealerChain.size() > 1) {
                        // 有多级经销商，需要计算每个经销商的分润
                        // 逻辑：下级经销商的commission_rate表示从总池子中抽取的比例
                        //      上级经销商分润 = 总池子 × (100% - 所有下级的rate之和)
                        BigDecimal dealerPoolAmount = order.getDealerAmount();

                        // 计算所有下级经销商的分润比例之和
                        BigDecimal totalSubRate = BigDecimal.ZERO;
                        for (int i = 1; i < dealerChain.size(); i++) {
                            DeviceDealer dd = dealerChain.get(i);
                            BigDecimal rate = dd.getCommissionRate() != null ? dd.getCommissionRate() : BigDecimal.ZERO;
                            totalSubRate = totalSubRate.add(rate);
                        }

                        for (int i = 0; i < dealerChain.size(); i++) {
                            DeviceDealer dd = dealerChain.get(i);

                            // 如果指定了经销商过滤，只统计该经销商的数据
                            boolean shouldCountSubDealer = (effectiveDealerId == null || dd.getDealerId().equals(effectiveDealerId));
                            if (!shouldCountSubDealer) {
                                continue;
                            }

                            // 跳过一级经销商（已经在上面统计过了）
                            if (i == 0) {
                                continue;
                            }

                            // 计算该下级经销商的实际所得 = 总池子 × 该经销商的rate
                            BigDecimal rate = dd.getCommissionRate() != null ? dd.getCommissionRate() : BigDecimal.ZERO;
                            BigDecimal actualAmount = dealerPoolAmount.multiply(rate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

                            // 为下级经销商创建或更新统计
                            String subKey = String.valueOf(dd.getDealerId());
                            final Long subDealerId = dd.getDealerId();
                            final String subInstallerCode = iCode;
                            Map<String, Object> subStat = dealerStats.computeIfAbsent(subKey, k -> {
                                Map<String, Object> s = new HashMap<>();
                                s.put("dealerId", subDealerId);
                                s.put("dealerCode", null);
                                s.put("dealerName", null);
                                s.put("installerCode", subInstallerCode);
                                s.put("orderCount", 0);
                                s.put("totalAmount", BigDecimal.ZERO);
                                s.put("dealerAmount", BigDecimal.ZERO);
                                s.put("settledAmount", BigDecimal.ZERO);
                                s.put("unsettledAmount", BigDecimal.ZERO);
                                s.put("totalAmountByCurrency", new HashMap<String, BigDecimal>());
                                s.put("dealerAmountByCurrency", new HashMap<String, BigDecimal>());
                                s.put("settledAmountByCurrency", new HashMap<String, BigDecimal>());
                                s.put("unsettledAmountByCurrency", new HashMap<String, BigDecimal>());
                                return s;
                            });

                            String currency = normalizeCurrency(order.getCurrency());
                            BigDecimal orderAmount = safeAmount(order.getAmount());
                            boolean isSettled = order.getIsSettled() != null && order.getIsSettled() == 1;

                            // 累加下级经销商的分润
                            subStat.put("orderCount", (Integer) subStat.get("orderCount") + 1);
                            subStat.put("totalAmount", ((BigDecimal) subStat.get("totalAmount")).add(orderAmount));
                            subStat.put("dealerAmount", ((BigDecimal) subStat.get("dealerAmount")).add(actualAmount));
                            addCurrencyAmount(getOrCreateCurrencyMap(subStat, "totalAmountByCurrency"), currency, orderAmount);
                            addCurrencyAmount(getOrCreateCurrencyMap(subStat, "dealerAmountByCurrency"), currency, actualAmount);

                            if (isSettled) {
                                subStat.put("settledAmount", ((BigDecimal) subStat.get("settledAmount")).add(actualAmount));
                                addCurrencyAmount(getOrCreateCurrencyMap(subStat, "settledAmountByCurrency"), currency, actualAmount);
                                totalSettledDealerAmount = totalSettledDealerAmount.add(actualAmount);
                                addCurrencyAmount(totalSettledDealerAmountByCurrency, currency, actualAmount);
                            } else {
                                subStat.put("unsettledAmount", ((BigDecimal) subStat.get("unsettledAmount")).add(actualAmount));
                                addCurrencyAmount(getOrCreateCurrencyMap(subStat, "unsettledAmountByCurrency"), currency, actualAmount);
                                totalUnsettledDealerAmount = totalUnsettledDealerAmount.add(actualAmount);
                                addCurrencyAmount(totalUnsettledDealerAmountByCurrency, currency, actualAmount);
                            }

                            totalDealerAmount = totalDealerAmount.add(actualAmount);
                            addCurrencyAmount(totalDealerAmountByCurrency, currency, actualAmount);
                        }

                        // 如果一级经销商需要统计，重新计算其分润（因为上面统计的是全部池子）
                        if (dealerChain.size() > 1) {
                            DeviceDealer topDealer = dealerChain.get(0);
                            boolean shouldCountTopDealer = (effectiveDealerId == null || topDealer.getDealerId().equals(effectiveDealerId));

                            if (shouldCountTopDealer && did != null && did.equals(topDealer.getDealerId())) {
                                // 一级经销商的实际分润 = 总池子 × (100% - 所有下级的rate之和)
                                BigDecimal topRate = new BigDecimal("100").subtract(totalSubRate);
                                BigDecimal topActualAmount = dealerPoolAmount.multiply(topRate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

                                // 修正一级经销商的分润金额（之前统计的是全部池子）
                                BigDecimal diff = order.getDealerAmount().subtract(topActualAmount);
                                if (diff.compareTo(BigDecimal.ZERO) != 0) {
                                    // 获取一级经销商的统计对象
                                    String topKey = String.valueOf(topDealer.getDealerId());
                                    Map<String, Object> topStat = dealerStats.get(topKey);
                                    if (topStat != null) {
                                        topStat.put("dealerAmount", ((BigDecimal) topStat.get("dealerAmount")).subtract(diff));
                                        String currency = normalizeCurrency(order.getCurrency());
                                        addCurrencyAmount(getOrCreateCurrencyMap(topStat, "dealerAmountByCurrency"), currency, diff.negate());

                                        boolean isSettled = order.getIsSettled() != null && order.getIsSettled() == 1;
                                        if (isSettled) {
                                            topStat.put("settledAmount", ((BigDecimal) topStat.get("settledAmount")).subtract(diff));
                                            addCurrencyAmount(getOrCreateCurrencyMap(topStat, "settledAmountByCurrency"), currency, diff.negate());
                                            totalSettledDealerAmount = totalSettledDealerAmount.subtract(diff);
                                            addCurrencyAmount(totalSettledDealerAmountByCurrency, currency, diff.negate());
                                        } else {
                                            topStat.put("unsettledAmount", ((BigDecimal) topStat.get("unsettledAmount")).subtract(diff));
                                            addCurrencyAmount(getOrCreateCurrencyMap(topStat, "unsettledAmountByCurrency"), currency, diff.negate());
                                            totalUnsettledDealerAmount = totalUnsettledDealerAmount.subtract(diff);
                                            addCurrencyAmount(totalUnsettledDealerAmountByCurrency, currency, diff.negate());
                                        }

                                        totalDealerAmount = totalDealerAmount.subtract(diff);
                                        addCurrencyAmount(totalDealerAmountByCurrency, currency, diff.negate());
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("查询设备经销商链路失败（device_dealer表可能不存在）: deviceId={}, error={}",
                            order.getDeviceId(), e.getMessage());
                }
            }

            String currency = normalizeCurrency(order.getCurrency());
            BigDecimal orderAmount = safeAmount(order.getAmount());
            BigDecimal installerAmt = order.getInstallerAmount() != null ? order.getInstallerAmount() : BigDecimal.ZERO;
            BigDecimal planCost = safeAmount(order.getPlanCost());
            BigDecimal dAmt = order.getDealerAmount() != null ? order.getDealerAmount() : BigDecimal.ZERO;

            totalAmount = totalAmount.add(orderAmount);
            totalCost = totalCost.add(planCost);
            BigDecimal effectiveFeeAmount = calculateEffectiveFeeAmount(order);
            BigDecimal effectiveProfitAmount = calculateEffectiveProfitAmount(order, effectiveFeeAmount);
            totalProfitAmount = totalProfitAmount.add(effectiveProfitAmount);
            totalInstallerAmount = totalInstallerAmount.add(installerAmt);
            if (shouldCountThisDealer) {
                totalDealerAmount = totalDealerAmount.add(dAmt);
            }
            addCurrencyAmount(totalAmountByCurrency, currency, orderAmount);
            addCurrencyAmount(totalCostByCurrency, currency, planCost);
            addCurrencyAmount(totalProfitAmountByCurrency, currency, effectiveProfitAmount);
            addCurrencyAmount(totalInstallerAmountByCurrency, currency, installerAmt);
            if (shouldCountThisDealer) {
                addCurrencyAmount(totalDealerAmountByCurrency, currency, dAmt);
            }
            if (order.getDeviceId() != null) {
                deviceIds.add(order.getDeviceId());
            }
            totalOrders++;
        }

        // 补充经销商名称
        List<Map<String, Object>> dealerList = new ArrayList<>(dealerStats.values());
        for (Map<String, Object> stat : dealerList) {
            Long did = (Long) stat.get("dealerId");
            if (did != null) {
                Dealer dealer = dealerRepository.selectById(did);
                if (dealer != null) {
                    stat.put("dealerCode", dealer.getDealerCode());
                    stat.put("dealerName", dealer.getName());
                }
            }
        }

        for (Map<String, Object> stat : dealerList) {
            stat.put("totalAmountByCurrency", toOrderedCurrencyAmountMap(getOrCreateCurrencyMap(stat, "totalAmountByCurrency")));
            stat.put("dealerAmountByCurrency", toOrderedCurrencyAmountMap(getOrCreateCurrencyMap(stat, "dealerAmountByCurrency")));
            stat.put("settledAmountByCurrency", toOrderedCurrencyAmountMap(getOrCreateCurrencyMap(stat, "settledAmountByCurrency")));
            stat.put("unsettledAmountByCurrency", toOrderedCurrencyAmountMap(getOrCreateCurrencyMap(stat, "unsettledAmountByCurrency")));
        }

        BigDecimal totalRemainingProfit = calculateRemainingProfit(
                totalProfitAmount, totalInstallerAmount, totalDealerAmount, "dealer");

        List<Map<String, Object>> currencyStats = buildCurrencySummaryStats(
                totalAmountByCurrency,
                totalCostByCurrency,
                totalProfitAmountByCurrency,
                totalInstallerAmountByCurrency,
                totalDealerAmountByCurrency,
                totalSettledDealerAmountByCurrency,
                totalUnsettledDealerAmountByCurrency,
                "dealer");

        Map<String, BigDecimal> totalRemainingProfitByCurrency = new HashMap<>();
        for (Map<String, Object> currencyStat : currencyStats) {
            String currency = (String) currencyStat.get("currency");
            BigDecimal remaining = (BigDecimal) currencyStat.get("totalRemainingProfit");
            totalRemainingProfitByCurrency.put(currency, remaining);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", dealerList);
        result.put("totalOrders", totalOrders);
        result.put("totalDevices", deviceIds.size());
        result.put("totalRevenue", totalAmount);
        result.put("totalAmount", totalAmount);
        result.put("totalCost", totalCost);
        result.put("totalProfitAmount", totalProfitAmount);
        result.put("totalInstallerAmount", totalInstallerAmount);
        result.put("totalDealerAmount", totalDealerAmount);
        result.put("totalRemainingProfit", totalRemainingProfit);
        result.put("totalSettledDealerAmount", totalSettledDealerAmount);
        result.put("totalUnsettledDealerAmount", totalUnsettledDealerAmount);
        result.put("currencyStats", currencyStats);
        result.put("totalRevenueByCurrency", toOrderedCurrencyAmountMap(totalAmountByCurrency));
        result.put("totalAmountByCurrency", toOrderedCurrencyAmountMap(totalAmountByCurrency));
        result.put("totalCostByCurrency", toOrderedCurrencyAmountMap(totalCostByCurrency));
        result.put("totalProfitAmountByCurrency", toOrderedCurrencyAmountMap(totalProfitAmountByCurrency));
        result.put("totalInstallerAmountByCurrency", toOrderedCurrencyAmountMap(totalInstallerAmountByCurrency));
        result.put("totalDealerAmountByCurrency", toOrderedCurrencyAmountMap(totalDealerAmountByCurrency));
        result.put("totalRemainingProfitByCurrency", toOrderedCurrencyAmountMap(totalRemainingProfitByCurrency));
        result.put("totalSettledAmountByCurrency", toOrderedCurrencyAmountMap(totalSettledDealerAmountByCurrency));
        result.put("totalUnsettledAmountByCurrency", toOrderedCurrencyAmountMap(totalUnsettledDealerAmountByCurrency));
        return result;
    }

    /**
     * 获取订单明细列表（用于导出）
     * @param installerCode 装机商代码（可选）
     * @param dealerId 经销商ID（可选）
     * @param startDate 开始日期
     * @param endDate 结束日期
     */
    public List<Map<String, Object>> getOrderDetails(Long currentUserId, String installerCode, Long dealerId, Date startDate, Date endDate) {
        BillingDataScope scope = resolveBillingDataScope(currentUserId, installerCode, dealerId);

        QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();
        qw.lambda().eq(PaymentOrder::getStatus, PaymentOrderStatus.PAID);
        applyInstallerDealerScope(qw, scope);
        if (startDate != null) {
            qw.lambda().ge(PaymentOrder::getPaidAt, startDate);
        }
        if (endDate != null) {
            qw.lambda().le(PaymentOrder::getPaidAt, endDate);
        }
        qw.lambda().orderByDesc(PaymentOrder::getPaidAt);

        List<PaymentOrder> orders = orderRepository.selectList(qw);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        List<Map<String, Object>> result = new ArrayList<>();
        for (PaymentOrder order : orders) {
            Map<String, Object> row = new HashMap<>();
            row.put("orderId", order.getOrderId());
            row.put("deviceId", order.getDeviceId());
            row.put("installerCode", order.getInstallerCode());
            row.put("dealerId", order.getDealerId());
            row.put("dealerCode", order.getDealerCode());
            row.put("dealerName", getDealerName(order.getDealerId()));
            row.put("productType", order.getProductType());
            row.put("productId", order.getProductId());
            row.put("amount", order.getAmount());
            row.put("commissionRate", scope.showDealerInfo ? order.getCommissionRate() : null);
            row.put("installerAmount", scope.showInstallerInfo ? order.getInstallerAmount() : null);
            row.put("dealerAmount", scope.showDealerInfo ? order.getDealerAmount() : null);
            row.put("paymentMethod", order.getPaymentMethod());
            row.put("paidAt", order.getPaidAt() != null ? sdf.format(order.getPaidAt()) : "");
            row.put("refundAt", order.getRefundAt() != null ? sdf.format(order.getRefundAt()) : "");
            row.put("refundReason", order.getRefundReason());
            result.add(row);
        }
        return result;
    }

    /**
     * 分页查询订单列表
     * 根据当前用户角色自动过滤：
     * - 管理员(role=3)：可查看所有订单
     * - 装机商(isInstaller=1)：只能查看自己关联的订单
     * - 经销商(isDealer=1)：只能查看自己关联的订单
     * - 既是装机商又是经销商：可查看两者关联的订单(OR条件)
     */
    public Map<String, Object> listOrders(Long currentUserId, Integer page, Integer size, String installerCode, Long dealerId, 
                                          String deviceId, String status, Date startDate, Date endDate) {
        // 根据当前用户角色确定过滤条件
        String effectiveInstallerCode = installerCode;
        Long effectiveDealerId = dealerId;
        boolean needOrCondition = false; // dual-role OR scope
        PaymentOrderStatus effectiveStatus = PaymentOrderStatus.PAID;
        if (status != null && !status.trim().isEmpty()) {
            PaymentOrderStatus statusEnum = PaymentOrderStatus.fromCode(status);
            if (statusEnum != null) {
                effectiveStatus = statusEnum;
            }
        }
        
        if (currentUserId != null) {
            User currentUser = userRepository.selectById(currentUserId);
            if (currentUser != null) {
                // 管理员可以查看所有订单，不需要强制过滤
                boolean isAdmin = currentUser.getRole() != null && currentUser.getRole() == 3;
                
                if (!isAdmin) {
                    boolean isInstaller = currentUser.getIsInstaller() != null && currentUser.getIsInstaller() == 1 && currentUser.getInstallerId() != null;
                    boolean isDealer = currentUser.getIsDealer() != null && currentUser.getIsDealer() == 1 && currentUser.getDealerId() != null;
                    
                    // 装机商只能查看自己关联的订单
                    if (isInstaller) {
                        Installer installer = installerRepository.selectById(currentUser.getInstallerId());
                        if (installer != null && installer.getInstallerCode() != null) {
                            effectiveInstallerCode = installer.getInstallerCode();
                            log.info("装机商用户查询订单列表, userId={}, installerCode={}", currentUserId, effectiveInstallerCode);
                        }
                    }
                    
                    // 经销商只能查看自己关联的订单
                    if (isDealer) {
                        effectiveDealerId = currentUser.getDealerId();
                        log.info("经销商用户查询订单列表, userId={}, dealerId={}", currentUserId, effectiveDealerId);
                    }
                    
                    // 既是装机商又是经销商，需要OR条件
                    needOrCondition = isInstaller && isDealer;
                }
            }
        }
        
        QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();
        
        // 如果既是装机商又是经销商，使用OR条件
        if (needOrCondition && effectiveInstallerCode != null && effectiveDealerId != null) {
            final String finalInstallerCode = effectiveInstallerCode;
            final Long finalDealerId = effectiveDealerId;
            qw.and(wrapper -> wrapper
                .eq("installer_code", finalInstallerCode)
                .or()
                .eq("dealer_id", finalDealerId)
            );
        } else {
            if (effectiveInstallerCode != null && !effectiveInstallerCode.trim().isEmpty()) {
                qw.lambda().eq(PaymentOrder::getInstallerCode, effectiveInstallerCode);
            }
            if (effectiveDealerId != null) {
                qw.lambda().eq(PaymentOrder::getDealerId, effectiveDealerId);
            }
        }
        if (deviceId != null && !deviceId.trim().isEmpty()) {
            qw.lambda().like(PaymentOrder::getDeviceId, deviceId);
        }
        qw.lambda().eq(PaymentOrder::getStatus, effectiveStatus);
        if (startDate != null) {
            qw.lambda().ge(PaymentOrder::getPaidAt, startDate);
        }
        if (endDate != null) {
            qw.lambda().le(PaymentOrder::getPaidAt, endDate);
        }
        qw.lambda().orderByDesc(PaymentOrder::getPaidAt);

        // 分页查询
        int offset = (page - 1) * size;
        qw.last("LIMIT " + offset + ", " + size);
        List<PaymentOrder> list = orderRepository.selectList(qw);
        for (PaymentOrder order : list) {
            normalizeFinancialFieldsForDisplay(order);
        }

        // 查询总数
        QueryWrapper<PaymentOrder> countQw = new QueryWrapper<>();
        // 如果既是装机商又是经销商，使用OR条件
        if (needOrCondition && effectiveInstallerCode != null && effectiveDealerId != null) {
            final String finalInstallerCode = effectiveInstallerCode;
            final Long finalDealerId = effectiveDealerId;
            countQw.and(wrapper -> wrapper
                .eq("installer_code", finalInstallerCode)
                .or()
                .eq("dealer_id", finalDealerId)
            );
        } else {
            if (effectiveInstallerCode != null && !effectiveInstallerCode.trim().isEmpty()) {
                countQw.lambda().eq(PaymentOrder::getInstallerCode, effectiveInstallerCode);
            }
            if (effectiveDealerId != null) {
                countQw.lambda().eq(PaymentOrder::getDealerId, effectiveDealerId);
            }
        }
        if (deviceId != null && !deviceId.trim().isEmpty()) {
            countQw.lambda().like(PaymentOrder::getDeviceId, deviceId);
        }
        countQw.lambda().eq(PaymentOrder::getStatus, effectiveStatus);
        if (startDate != null) {
            countQw.lambda().ge(PaymentOrder::getPaidAt, startDate);
        }
        if (endDate != null) {
            countQw.lambda().le(PaymentOrder::getPaidAt, endDate);
        }
        long total = orderRepository.selectCount(countQw);

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    /**
     * 记录退款
     * @param orderId 订单ID
     * @param reason 退款原因
     */
    public Map<String, Object> listOrders(Long currentUserId, Integer page, Integer size, String dimension,
                                          String installerCode, Long dealerId, String deviceId,
                                          String status, Date startDate, Date endDate) {
        BillingDataScope scope = resolveBillingDataScope(currentUserId, installerCode, dealerId, dimension);

        PaymentOrderStatus effectiveStatus = PaymentOrderStatus.PAID;
        if (hasText(status)) {
            PaymentOrderStatus statusEnum = PaymentOrderStatus.fromCode(status);
            if (statusEnum != null) {
                effectiveStatus = statusEnum;
            }
        }

        QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();
        applyInstallerDealerScope(qw, scope);
        if (hasText(deviceId)) {
            qw.lambda().like(PaymentOrder::getDeviceId, deviceId);
        }
        qw.lambda().eq(PaymentOrder::getStatus, effectiveStatus);
        if (startDate != null) {
            qw.lambda().ge(PaymentOrder::getPaidAt, startDate);
        }
        if (endDate != null) {
            qw.lambda().le(PaymentOrder::getPaidAt, endDate);
        }
        qw.lambda().orderByDesc(PaymentOrder::getPaidAt);

        int offset = (page - 1) * size;
        qw.last("LIMIT " + offset + ", " + size);
        List<PaymentOrder> list = orderRepository.selectList(qw);
        for (PaymentOrder order : list) {
            normalizeFinancialFieldsForDisplay(order);
        }
        maskOrderCommissionFields(list, scope.showInstallerInfo, scope.showDealerInfo);

        QueryWrapper<PaymentOrder> countQw = new QueryWrapper<>();
        applyInstallerDealerScope(countQw, scope);
        if (hasText(deviceId)) {
            countQw.lambda().like(PaymentOrder::getDeviceId, deviceId);
        }
        countQw.lambda().eq(PaymentOrder::getStatus, effectiveStatus);
        if (startDate != null) {
            countQw.lambda().ge(PaymentOrder::getPaidAt, startDate);
        }
        if (endDate != null) {
            countQw.lambda().le(PaymentOrder::getPaidAt, endDate);
        }
        long total = orderRepository.selectCount(countQw);

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    @Transactional
    public void recordRefund(String orderId, String reason) {
        QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();
        qw.lambda().eq(PaymentOrder::getOrderId, orderId);
        PaymentOrder order = orderRepository.selectOne(qw);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (order.getStatus() != PaymentOrderStatus.PAID) {
            throw new RuntimeException("只有已支付的订单才能退款");
        }
        order.setStatus(PaymentOrderStatus.REFUNDED);
        order.setRefundAt(new Date());
        order.setRefundReason(reason);
        order.setUpdatedAt(new Date());
        orderRepository.updateById(order);
        log.info("记录退款: orderId={}, reason={}", orderId, reason);
    }

    // 充值明细Excel列标题
    private static final String[] BILLING_DETAIL_HEADERS = {
            "订单号", "设备ID", "上线国家", "装机商代码", "装机商名称", "经销商ID", "经销商名称",
            "套餐名称", "套餐类型", "支付金额", "支付币种", "支付通道", "支付时间",
            "手续费", "套餐成本", "可分利润", "装机商分润", "经销商分润", "已结算"
    };

    private static final int BILLING_COL_INSTALLER_CODE = 3;
    private static final int BILLING_COL_INSTALLER_NAME = 4;
    private static final int BILLING_COL_DEALER_ID = 5;
    private static final int BILLING_COL_DEALER_NAME = 6;
    private static final int BILLING_COL_AMOUNT = 9;
    private static final int BILLING_COL_FEE = 13;
    private static final int BILLING_COL_PLAN_COST = 14;
    private static final int BILLING_COL_PROFIT = 15;
    private static final int BILLING_COL_INSTALLER_AMOUNT = 16;
    private static final int BILLING_COL_DEALER_AMOUNT = 17;

    private static final int EXCEL_BATCH_SIZE = 10000;

    /**
     * 导出充值明细Excel
     * 如果数据超过1万条，分成多个Excel文件并打包成zip
     * 权限说明：非管理员只能导出与自己关联的数据
     * @return Map包含 "isZip" (boolean) 和 "data" (byte[])
     */
    public Map<String, Object> exportBillingDetailExcel(Long currentUserId, String dimension, String installerCode,
                                                        Long dealerId, String deviceId, Date startDate,
                                                        Date endDate) throws IOException {
        BillingDataScope scope = resolveBillingDataScope(currentUserId, installerCode, dealerId, dimension);

        QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();
        qw.lambda().eq(PaymentOrder::getStatus, PaymentOrderStatus.PAID);
        applyInstallerDealerScope(qw, scope);
        if (hasText(deviceId)) {
            qw.lambda().like(PaymentOrder::getDeviceId, deviceId);
        }
        if (startDate != null) {
            qw.lambda().ge(PaymentOrder::getPaidAt, startDate);
        }
        if (endDate != null) {
            qw.lambda().le(PaymentOrder::getPaidAt, endDate);
        }
        qw.lambda().orderByDesc(PaymentOrder::getPaidAt);

        List<PaymentOrder> orders = orderRepository.selectList(qw);
        for (PaymentOrder order : orders) {
            normalizeFinancialFieldsForDisplay(order);
        }

        Map<String, Object> result = new HashMap<>();
        if (orders.size() <= EXCEL_BATCH_SIZE) {
            byte[] excelData = createBillingDetailExcel(orders, null, scope.showInstallerInfo, scope.showDealerInfo);
            result.put("isZip", false);
            result.put("data", excelData);
        } else {
            byte[] zipData = createBillingDetailZip(orders, scope.showInstallerInfo, scope.showDealerInfo);
            result.put("isZip", true);
            result.put("data", zipData);
        }
        return result;
    }
    public Map<String, Object> exportBillingDetailExcel(Long currentUserId, String installerCode, Long dealerId, String deviceId, Date startDate, Date endDate) throws IOException {
        BillingDataScope scope = resolveBillingDataScope(currentUserId, installerCode, dealerId);
        
        log.info("开始导出充值明细Excel: currentUserId={}, installerCode={}, dealerId={}, deviceId={}, startDate={}, endDate={}", 
                currentUserId, scope.installerCode, scope.dealerId, deviceId, startDate, endDate);

        // 查询所有符合条件的订单
        QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();
        qw.lambda().eq(PaymentOrder::getStatus, PaymentOrderStatus.PAID);
        applyInstallerDealerScope(qw, scope);
        if (deviceId != null && !deviceId.trim().isEmpty()) {
            qw.lambda().like(PaymentOrder::getDeviceId, deviceId);
        }
        if (startDate != null) {
            qw.lambda().ge(PaymentOrder::getPaidAt, startDate);
        }
        if (endDate != null) {
            qw.lambda().le(PaymentOrder::getPaidAt, endDate);
        }
        qw.lambda().orderByDesc(PaymentOrder::getPaidAt);

        List<PaymentOrder> orders = orderRepository.selectList(qw);
        for (PaymentOrder order : orders) {
            normalizeFinancialFieldsForDisplay(order);
        }
        log.info("查询到充值明细订单数: {}", orders.size());

        Map<String, Object> result = new HashMap<>();

        if (orders.size() <= EXCEL_BATCH_SIZE) {
            // 单个Excel文件
            byte[] excelData = createBillingDetailExcel(orders, null, scope.showInstallerInfo, scope.showDealerInfo);
            result.put("isZip", false);
            result.put("data", excelData);
            log.info("导出单个Excel文件完成");
        } else {
            // 分片打包成zip
            byte[] zipData = createBillingDetailZip(orders, scope.showInstallerInfo, scope.showDealerInfo);
            result.put("isZip", true);
            result.put("data", zipData);
            log.info("导出Zip文件完成，包含 {} 个Excel文件", (orders.size() + EXCEL_BATCH_SIZE - 1) / EXCEL_BATCH_SIZE);
        }

        return result;
    }

    /**
     * 创建充值明细Excel
     */
    private byte[] createBillingDetailExcel(List<PaymentOrder> orders, String sheetName) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(sheetName != null ? sheetName : "充值明细");

            // 创建标题样式
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // 创建数据样式
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            // 创建金额样式
            CellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.setBorderBottom(BorderStyle.THIN);
            moneyStyle.setBorderTop(BorderStyle.THIN);
            moneyStyle.setBorderLeft(BorderStyle.THIN);
            moneyStyle.setBorderRight(BorderStyle.THIN);
            DataFormat format = workbook.createDataFormat();
            moneyStyle.setDataFormat(format.getFormat("#,##0.00"));

            // 写入标题行
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < BILLING_DETAIL_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(BILLING_DETAIL_HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // 写入数据行
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            int rowNum = 1;
            for (PaymentOrder order : orders) {
                Row row = sheet.createRow(rowNum++);
                int col = 0;

                // 订单号
                createTextCell(row, col++, order.getOrderId(), dataStyle);
                // 设备ID
                createTextCell(row, col++, order.getDeviceId(), dataStyle);
                // 上线国家
                createTextCell(row, col++, order.getOnlineCountry(), dataStyle);
                // 装机商代码
                createTextCell(row, col++, order.getInstallerCode(), dataStyle);
                // 装机商名称
                String installerName = getInstallerName(order.getInstallerId());
                createTextCell(row, col++, installerName, dataStyle);
                // 经销商ID
                createTextCell(row, col++, order.getDealerId() != null ? String.valueOf(order.getDealerId()) : "", dataStyle);
                // 经销商名称
                String dealerName = getDealerName(order.getDealerId());
                createTextCell(row, col++, dealerName, dataStyle);
                // 套餐名称
                String productName = getProductName(order.getProductId());
                createTextCell(row, col++, productName, dataStyle);
                // 套餐类型
                createTextCell(row, col++, order.getProductType(), dataStyle);
                // 支付金额
                createMoneyCell(row, col++, order.getAmount(), moneyStyle);
                // 支付币种
                createTextCell(row, col++, order.getCurrency(), dataStyle);
                // 支付通道
                createTextCell(row, col++, order.getPaymentMethod(), dataStyle);
                // 支付时间
                createTextCell(row, col++, order.getPaidAt() != null ? sdf.format(order.getPaidAt()) : "", dataStyle);
                // 手续费
                createMoneyCell(row, col++, order.getFeeAmount(), moneyStyle);
                // 套餐成本
                createMoneyCell(row, col++, order.getPlanCost(), moneyStyle);
                // 可分利润
                createMoneyCell(row, col++, order.getProfitAmount(), moneyStyle);
                // 装机商分润
                createMoneyCell(row, col++, order.getInstallerAmount(), moneyStyle);
                // 经销商分润
                createMoneyCell(row, col++, order.getDealerAmount(), moneyStyle);
                // 已结算
                createTextCell(row, col++, order.getIsSettled() != null && order.getIsSettled() == 1 ? "是" : "否", dataStyle);
            }

            // 自动调整列宽
            for (int i = 0; i < BILLING_DETAIL_HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
                int width = sheet.getColumnWidth(i);
                if (width < 3000) {
                    sheet.setColumnWidth(i, 3000);
                } else if (width > 15000) {
                    sheet.setColumnWidth(i, 15000);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] createBillingDetailExcel(List<PaymentOrder> orders, String sheetName,
                                            boolean showInstallerInfo,
                                            boolean showDealerInfo) throws IOException {
        byte[] excelData = createBillingDetailExcel(orders, sheetName);
        if (showInstallerInfo && showDealerInfo) {
            return excelData;
        }
        return trimBillingDetailColumns(excelData, showInstallerInfo, showDealerInfo);
    }

    private byte[] trimBillingDetailColumns(byte[] sourceExcel,
                                            boolean showInstallerInfo,
                                            boolean showDealerInfo) throws IOException {
        List<Integer> hiddenColumns = new ArrayList<>();
        if (!showInstallerInfo) {
            hiddenColumns.add(BILLING_COL_INSTALLER_CODE);
            hiddenColumns.add(BILLING_COL_INSTALLER_NAME);
            hiddenColumns.add(BILLING_COL_INSTALLER_AMOUNT);
        }
        if (!showDealerInfo) {
            hiddenColumns.add(BILLING_COL_DEALER_ID);
            hiddenColumns.add(BILLING_COL_DEALER_NAME);
            hiddenColumns.add(BILLING_COL_DEALER_AMOUNT);
        }

        if (hiddenColumns.isEmpty()) {
            return sourceExcel;
        }
        hiddenColumns.sort(Collections.reverseOrder());

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(sourceExcel));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet != null) {
                for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row == null) {
                        continue;
                    }
                    removeColumnsFromRow(row, hiddenColumns);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void removeColumnsFromRow(Row row, List<Integer> columnIndexesDesc) {
        int lastCellNum = row.getLastCellNum();
        if (lastCellNum <= 0) {
            return;
        }
        for (Integer columnIndex : columnIndexesDesc) {
            if (columnIndex == null || columnIndex < 0 || columnIndex >= lastCellNum) {
                continue;
            }
            shiftRowCellsLeft(row, columnIndex, lastCellNum);
            lastCellNum--;
        }
    }

    private void shiftRowCellsLeft(Row row, int fromColumn, int lastCellNum) {
        for (int col = fromColumn; col < lastCellNum - 1; col++) {
            Cell nextCell = row.getCell(col + 1);
            Cell targetCell = row.getCell(col);

            if (nextCell == null) {
                if (targetCell != null) {
                    row.removeCell(targetCell);
                }
                continue;
            }

            if (targetCell == null) {
                targetCell = row.createCell(col);
            }
            copyCell(nextCell, targetCell);
        }

        Cell lastCell = row.getCell(lastCellNum - 1);
        if (lastCell != null) {
            row.removeCell(lastCell);
        }
    }

    private void copyCell(Cell source, Cell target) {
        target.setCellStyle(source.getCellStyle());
        switch (source.getCellType()) {
            case STRING:
                target.setCellValue(source.getStringCellValue());
                break;
            case NUMERIC:
                target.setCellValue(source.getNumericCellValue());
                break;
            case BOOLEAN:
                target.setCellValue(source.getBooleanCellValue());
                break;
            case FORMULA:
                target.setCellFormula(source.getCellFormula());
                break;
            case ERROR:
                target.setCellErrorValue(source.getErrorCellValue());
                break;
            case BLANK:
                target.setCellValue("");
                break;
            default:
                target.setCellValue("");
                break;
        }
    }

    /**
     * 创建充值明细Zip（包含多个Excel文件）
     */
    private byte[] createBillingDetailZip(List<PaymentOrder> orders) throws IOException {
        ByteArrayOutputStream zipOut = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(zipOut)) {
            int totalFiles = (orders.size() + EXCEL_BATCH_SIZE - 1) / EXCEL_BATCH_SIZE;
            for (int i = 0; i < totalFiles; i++) {
                int fromIndex = i * EXCEL_BATCH_SIZE;
                int toIndex = Math.min(fromIndex + EXCEL_BATCH_SIZE, orders.size());
                List<PaymentOrder> batch = orders.subList(fromIndex, toIndex);

                String fileName = String.format("充值明细_%d-%d.xlsx", fromIndex + 1, toIndex);
                byte[] excelData = createBillingDetailExcel(batch, "充值明细");

                ZipEntry entry = new ZipEntry(fileName);
                zos.putNextEntry(entry);
                zos.write(excelData);
                zos.closeEntry();
            }
        }
        return zipOut.toByteArray();
    }

    private byte[] createBillingDetailZip(List<PaymentOrder> orders,
                                          boolean showInstallerInfo,
                                          boolean showDealerInfo) throws IOException {
        ByteArrayOutputStream zipOut = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(zipOut)) {
            int totalFiles = (orders.size() + EXCEL_BATCH_SIZE - 1) / EXCEL_BATCH_SIZE;
            for (int i = 0; i < totalFiles; i++) {
                int fromIndex = i * EXCEL_BATCH_SIZE;
                int toIndex = Math.min(fromIndex + EXCEL_BATCH_SIZE, orders.size());
                List<PaymentOrder> batch = orders.subList(fromIndex, toIndex);

                String fileName = String.format("充值明细_%d-%d.xlsx", fromIndex + 1, toIndex);
                byte[] excelData = createBillingDetailExcel(batch, "充值明细", showInstallerInfo, showDealerInfo);

                ZipEntry entry = new ZipEntry(fileName);
                zos.putNextEntry(entry);
                zos.write(excelData);
                zos.closeEntry();
            }
        }
        return zipOut.toByteArray();
    }

    /**
     * 获取装机商名称
     */
    private String getInstallerName(Long installerId) {
        if (installerId == null) return "";
        Installer installer = installerRepository.selectById(installerId);
        return installer != null ? installer.getInstallerName() : "";
    }

    /**
     * 获取套餐名称
     */
    private String getProductName(String productId) {
        if (productId == null || productId.trim().isEmpty()) return "";
        QueryWrapper<CloudPlan> qw = new QueryWrapper<>();
        qw.lambda().eq(CloudPlan::getPlanId, productId);
        CloudPlan plan = cloudPlanRepository.selectOne(qw);
        return plan != null ? plan.getName() : productId;
    }

    /**
     * 获取经销商名称
     */
    private String getDealerName(Long dealerId) {
        if (dealerId == null) return "";
        Dealer dealer = dealerRepository.selectById(dealerId);
        return dealer != null ? dealer.getName() : "";
    }

    /**
     * 创建文本单元格
     */
    private void createTextCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    /**
     * 创建金额单元格
     */
    private void createMoneyCell(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        } else {
            cell.setCellValue(0.0);
        }
        cell.setCellStyle(style);
    }

    /**
     * 获取当前用户的账单汇总统计
     * 返回设备总数、经销商分润等汇总数据
     */
    public Map<String, Object> getMySummary(Long currentUserId, Date startDate, Date endDate) {
        Map<String, Object> result = new HashMap<>();
        
        if (currentUserId == null) {
            result.put("totalDevices", 0);
            result.put("totalDealerAmount", BigDecimal.ZERO);
            return result;
        }
        
        User currentUser = userRepository.selectById(currentUserId);
        if (currentUser == null) {
            result.put("totalDevices", 0);
            result.put("totalDealerAmount", BigDecimal.ZERO);
            return result;
        }
        
        // 统计设备总数
        long totalDevices = 0;
        String installerCode = null;
        Long dealerId = null;
        
        // 装机商用户
        if (currentUser.getIsInstaller() != null && currentUser.getIsInstaller() == 1 && currentUser.getInstallerId() != null) {
            Installer installer = installerRepository.selectById(currentUser.getInstallerId());
            if (installer != null) {
                installerCode = installer.getInstallerCode();
                // 统计装机商下的设备数
                QueryWrapper<ManufacturedDevice> deviceQw = new QueryWrapper<>();
                deviceQw.lambda().eq(ManufacturedDevice::getAssemblerCode, installerCode);
                totalDevices = deviceRepository.selectCount(deviceQw);
            }
        }
        
        // 经销商用户
        if (currentUser.getIsDealer() != null && currentUser.getIsDealer() == 1 && currentUser.getDealerId() != null) {
            dealerId = currentUser.getDealerId();
            Dealer dealer = dealerRepository.selectById(dealerId);
            if (dealer != null) {
                // 统计经销商下的设备数
                QueryWrapper<ManufacturedDevice> deviceQw = new QueryWrapper<>();
                deviceQw.lambda().eq(ManufacturedDevice::getVendorCode, dealer.getDealerCode());
                long dealerDevices = deviceRepository.selectCount(deviceQw);
                // 如果既是装机商又是经销商，取较大值
                totalDevices = Math.max(totalDevices, dealerDevices);
            }
        }
        
        // 统计经销商分润总额
        BigDecimal totalDealerAmount = BigDecimal.ZERO;
        if (dealerId != null) {
            QueryWrapper<PaymentOrder> orderQw = new QueryWrapper<>();
            orderQw.lambda().eq(PaymentOrder::getDealerId, dealerId)
                    .eq(PaymentOrder::getStatus, PaymentOrderStatus.PAID);
            if (startDate != null) {
                orderQw.lambda().ge(PaymentOrder::getPaidAt, startDate);
            }
            if (endDate != null) {
                orderQw.lambda().le(PaymentOrder::getPaidAt, endDate);
            }
            List<PaymentOrder> orders = orderRepository.selectList(orderQw);
            for (PaymentOrder order : orders) {
                if (order.getDealerAmount() != null) {
                    totalDealerAmount = totalDealerAmount.add(order.getDealerAmount());
                }
            }
        }
        
        result.put("totalDevices", totalDevices);
        result.put("totalDealerAmount", totalDealerAmount);
        
        log.info("获取用户账单汇总: userId={}, totalDevices={}, totalDealerAmount={}", 
                currentUserId, totalDevices, totalDealerAmount);
        return result;
    }

    /**
     * 获取装机商下设备的支付统计
     * @param installerId 装机商ID
     * @param startDate 开始日期
     * @param endDate 结束日期
     */
    public Map<String, Object> getDevicePaymentStats(Long installerId, Date startDate, Date endDate) {
        // 获取装机商下的所有设备
        QueryWrapper<ManufacturedDevice> deviceQw = new QueryWrapper<>();
        deviceQw.lambda().eq(ManufacturedDevice::getInstallerId, installerId);
        List<ManufacturedDevice> devices = deviceRepository.selectList(deviceQw);

        List<Map<String, Object>> deviceStats = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        int totalOrders = 0;

        for (ManufacturedDevice device : devices) {
            QueryWrapper<PaymentOrder> orderQw = new QueryWrapper<>();
            orderQw.lambda().eq(PaymentOrder::getDeviceId, device.getDeviceId())
                    .eq(PaymentOrder::getStatus, PaymentOrderStatus.PAID);
            if (startDate != null) {
                orderQw.lambda().ge(PaymentOrder::getPaidAt, startDate);
            }
            if (endDate != null) {
                orderQw.lambda().le(PaymentOrder::getPaidAt, endDate);
            }
            List<PaymentOrder> orders = orderRepository.selectList(orderQw);

            if (!orders.isEmpty()) {
                BigDecimal deviceTotal = BigDecimal.ZERO;
                for (PaymentOrder order : orders) {
                    deviceTotal = deviceTotal.add(order.getAmount() != null ? order.getAmount() : BigDecimal.ZERO);
                }

                Map<String, Object> stat = new HashMap<>();
                stat.put("deviceId", device.getDeviceId());
                stat.put("orderCount", orders.size());
                stat.put("totalAmount", deviceTotal);
                deviceStats.add(stat);

                totalAmount = totalAmount.add(deviceTotal);
                totalOrders += orders.size();
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", deviceStats);
        result.put("totalOrders", totalOrders);
        result.put("totalAmount", totalAmount);
        result.put("deviceCount", devices.size());
        return result;
    }

    /**
     * 批量结算订单
     * @param orderIds 订单ID列表
     * @param operatorId 操作员ID
     * @return 结算成功的订单数量
     */
    @Transactional
    public int settleOrders(List<String> orderIds, Long operatorId) {
        if (orderIds == null || orderIds.isEmpty()) {
            return 0;
        }
        log.info("批量结算订单: operatorId={}, orderIds={}", operatorId, orderIds);
        
        int count = 0;
        Date now = new Date();
        for (String orderId : orderIds) {
            QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();
            qw.lambda().eq(PaymentOrder::getOrderId, orderId)
                    .eq(PaymentOrder::getStatus, PaymentOrderStatus.PAID)
                    .eq(PaymentOrder::getIsSettled, 0);
            PaymentOrder order = orderRepository.selectOne(qw);
            if (order != null) {
                order.setIsSettled(1);
                order.setUpdatedAt(now);
                orderRepository.updateById(order);
                count++;
            }
        }
        log.info("结算完成: 成功结算 {} 笔订单", count);
        return count;
    }

    /**
     * 获取结算订单列表
     * @param installerCode 装机商代码（可选）
     * @param dealerId 经销商ID（可选）
     * @param isSettled 结算状态：null-全部，0-未结算，1-已结算
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param page 页码
     * @param size 每页条数
     */
    public Map<String, Object> getSettlementOrders(String installerCode, Long dealerId, Integer isSettled,
                                                    Date startDate, Date endDate, Integer page, Integer size) {
        QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();
        qw.lambda().eq(PaymentOrder::getStatus, PaymentOrderStatus.PAID);
        
        if (installerCode != null && !installerCode.trim().isEmpty()) {
            qw.lambda().eq(PaymentOrder::getInstallerCode, installerCode);
        }
        if (dealerId != null) {
            qw.lambda().eq(PaymentOrder::getDealerId, dealerId);
        }
        if (isSettled != null) {
            qw.lambda().eq(PaymentOrder::getIsSettled, isSettled);
        }
        if (startDate != null) {
            qw.lambda().ge(PaymentOrder::getPaidAt, startDate);
        }
        if (endDate != null) {
            qw.lambda().le(PaymentOrder::getPaidAt, endDate);
        }
        qw.lambda().orderByDesc(PaymentOrder::getPaidAt);

        // 分页查询
        int offset = (page - 1) * size;
        QueryWrapper<PaymentOrder> countQw = qw.clone();
        qw.last("LIMIT " + offset + ", " + size);
        List<PaymentOrder> list = orderRepository.selectList(qw);
        long total = orderRepository.selectCount(countQw);

        // 计算汇总数据
        QueryWrapper<PaymentOrder> sumQw = new QueryWrapper<>();
        sumQw.lambda().eq(PaymentOrder::getStatus, PaymentOrderStatus.PAID);
        if (installerCode != null && !installerCode.trim().isEmpty()) {
            sumQw.lambda().eq(PaymentOrder::getInstallerCode, installerCode);
        }
        if (dealerId != null) {
            sumQw.lambda().eq(PaymentOrder::getDealerId, dealerId);
        }
        if (startDate != null) {
            sumQw.lambda().ge(PaymentOrder::getPaidAt, startDate);
        }
        if (endDate != null) {
            sumQw.lambda().le(PaymentOrder::getPaidAt, endDate);
        }
        List<PaymentOrder> allOrders = orderRepository.selectList(sumQw);
        
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal settledAmount = BigDecimal.ZERO;
        BigDecimal unsettledAmount = BigDecimal.ZERO;
        BigDecimal totalProfitAmount = BigDecimal.ZERO;
        BigDecimal settledProfitAmount = BigDecimal.ZERO;
        BigDecimal unsettledProfitAmount = BigDecimal.ZERO;
        
        for (PaymentOrder order : allOrders) {
            BigDecimal amount = order.getAmount() != null ? order.getAmount() : BigDecimal.ZERO;
            // 根据查询类型确定分润金额（装机商/经销商）
            BigDecimal profit;
            if (dealerId != null) {
                profit = order.getDealerAmount() != null ? order.getDealerAmount() : BigDecimal.ZERO;
            } else {
                profit = order.getInstallerAmount() != null ? order.getInstallerAmount() : BigDecimal.ZERO;
            }
            
            totalAmount = totalAmount.add(amount);
            totalProfitAmount = totalProfitAmount.add(profit);
            
            if (order.getIsSettled() != null && order.getIsSettled() == 1) {
                settledAmount = settledAmount.add(amount);
                settledProfitAmount = settledProfitAmount.add(profit);
            } else {
                unsettledAmount = unsettledAmount.add(amount);
                unsettledProfitAmount = unsettledProfitAmount.add(profit);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("totalAmount", totalAmount);
        result.put("settledAmount", settledAmount);
        result.put("unsettledAmount", unsettledAmount);
        result.put("totalProfitAmount", totalProfitAmount);
        result.put("settledProfitAmount", settledProfitAmount);
        result.put("unsettledProfitAmount", unsettledProfitAmount);
        return result;
    }

    // 结算表Excel列标题
    private static final String[] SETTLEMENT_HEADERS = {
            "订单号", "设备ID", "上线国家", "装机商代码", "装机商名称", "经销商ID", "经销商名称",
            "套餐名称", "支付金额", "支付币种", "支付时间", "分润金额", "结算状态"
    };

    /**
     * 导出结算表Excel
     * 权限说明：非管理员只能导出与自己关联的数据
     */
    public Map<String, Object> exportSettlementExcel(Long currentUserId, String installerCode, Long dealerId, 
                                                      Date startDate, Date endDate) throws IOException {
        // 根据当前用户角色确定过滤条件
        String effectiveInstallerCode = installerCode;
        Long effectiveDealerId = dealerId;
        
        if (currentUserId != null) {
            User currentUser = userRepository.selectById(currentUserId);
            if (currentUser != null) {
                boolean isAdmin = currentUser.getRole() != null && currentUser.getRole() == 3;
                if (!isAdmin) {
                    // 装机商只能导出自己关联的数据
                    if (currentUser.getIsInstaller() != null && currentUser.getIsInstaller() == 1 && currentUser.getInstallerId() != null) {
                        Installer installer = installerRepository.selectById(currentUser.getInstallerId());
                        if (installer != null) {
                            effectiveInstallerCode = installer.getInstallerCode();
                        }
                    }
                    // 经销商只能导出自己关联的数据
                    if (currentUser.getIsDealer() != null && currentUser.getIsDealer() == 1 && currentUser.getDealerId() != null) {
                        effectiveDealerId = currentUser.getDealerId();
                    }
                }
            }
        }
        
        log.info("导出结算表: currentUserId={}, installerCode={}, dealerId={}, startDate={}, endDate={}", 
                currentUserId, effectiveInstallerCode, effectiveDealerId, startDate, endDate);

        QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();
        qw.lambda().eq(PaymentOrder::getStatus, PaymentOrderStatus.PAID);
        if (effectiveInstallerCode != null && !effectiveInstallerCode.trim().isEmpty()) {
            qw.lambda().eq(PaymentOrder::getInstallerCode, effectiveInstallerCode);
        }
        if (effectiveDealerId != null) {
            qw.lambda().eq(PaymentOrder::getDealerId, effectiveDealerId);
        }
        if (startDate != null) {
            qw.lambda().ge(PaymentOrder::getPaidAt, startDate);
        }
        if (endDate != null) {
            qw.lambda().le(PaymentOrder::getPaidAt, endDate);
        }
        qw.lambda().orderByDesc(PaymentOrder::getPaidAt);

        List<PaymentOrder> orders = orderRepository.selectList(qw);
        boolean isDealerSettlement = dealerId != null;
        
        byte[] excelData = createSettlementExcel(orders, isDealerSettlement);
        
        Map<String, Object> result = new HashMap<>();
        result.put("isZip", false);
        result.put("data", excelData);
        return result;
    }

    /**
     * 创建结算表Excel
     */
    private byte[] createSettlementExcel(List<PaymentOrder> orders, boolean isDealerSettlement) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("结算表");

            // 创建标题样式
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // 创建数据样式
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            // 创建金额样式
            CellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.setBorderBottom(BorderStyle.THIN);
            moneyStyle.setBorderTop(BorderStyle.THIN);
            moneyStyle.setBorderLeft(BorderStyle.THIN);
            moneyStyle.setBorderRight(BorderStyle.THIN);
            DataFormat format = workbook.createDataFormat();
            moneyStyle.setDataFormat(format.getFormat("#,##0.00"));

            // 写入标题行
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < SETTLEMENT_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(SETTLEMENT_HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // 写入数据行
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            int rowNum = 1;
            BigDecimal totalProfit = BigDecimal.ZERO;
            
            for (PaymentOrder order : orders) {
                Row row = sheet.createRow(rowNum++);
                int col = 0;

                createTextCell(row, col++, order.getOrderId(), dataStyle);
                createTextCell(row, col++, order.getDeviceId(), dataStyle);
                createTextCell(row, col++, order.getOnlineCountry(), dataStyle);
                createTextCell(row, col++, order.getInstallerCode(), dataStyle);
                createTextCell(row, col++, getInstallerName(order.getInstallerId()), dataStyle);
                createTextCell(row, col++, order.getDealerId() != null ? String.valueOf(order.getDealerId()) : "", dataStyle);
                createTextCell(row, col++, getDealerName(order.getDealerId()), dataStyle);
                createTextCell(row, col++, getProductName(order.getProductId()), dataStyle);
                createMoneyCell(row, col++, order.getAmount(), moneyStyle);
                createTextCell(row, col++, order.getCurrency(), dataStyle);
                createTextCell(row, col++, order.getPaidAt() != null ? sdf.format(order.getPaidAt()) : "", dataStyle);
                
                // 分润金额（根据类型）
                BigDecimal profit = isDealerSettlement ? 
                        (order.getDealerAmount() != null ? order.getDealerAmount() : BigDecimal.ZERO) :
                        (order.getInstallerAmount() != null ? order.getInstallerAmount() : BigDecimal.ZERO);
                createMoneyCell(row, col++, profit, moneyStyle);
                totalProfit = totalProfit.add(profit);
                
                createTextCell(row, col++, order.getIsSettled() != null && order.getIsSettled() == 1 ? "已结算" : "未结算", dataStyle);
            }

            // 写入汇总行
            Row sumRow = sheet.createRow(rowNum);
            Cell sumLabelCell = sumRow.createCell(0);
            sumLabelCell.setCellValue("合计");
            sumLabelCell.setCellStyle(headerStyle);
            
            Cell sumProfitCell = sumRow.createCell(11);
            sumProfitCell.setCellValue(totalProfit.doubleValue());
            sumProfitCell.setCellStyle(moneyStyle);

            // 自动调整列宽
            for (int i = 0; i < SETTLEMENT_HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
                int width = sheet.getColumnWidth(i);
                if (width < 3000) {
                    sheet.setColumnWidth(i, 3000);
                } else if (width > 15000) {
                    sheet.setColumnWidth(i, 15000);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
