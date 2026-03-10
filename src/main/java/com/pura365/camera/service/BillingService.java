package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.domain.*;
import com.pura365.camera.enums.PaymentOrderStatus;
import com.pura365.camera.repository.*;
import com.pura365.camera.util.MoneyScaleUtil;
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
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 闂備浇宕垫慨鐢稿礉瑜忕划濠氬箣濠靛牊娈鹃梺缁樻濞咃絿澹曟總鍛婄厽闁哄啠鍋撴俊鐐叉健瀹曘儵鍩€椤掑嫭鈷戦柛婵嗗婢ф洜绱掓径濠勬憼闁?
 * 闂傚倷绀佸﹢杈╁垝椤栫偛绀夐柟鐑樻⒐椤愪粙鏌ｉ姀銏℃毄缁炬儳銈搁悡顐﹀炊閵娧€濮囬梺杞扮劍閿曘垽寮?婵犵數鍋為崹鍫曞箰婵犳碍鍤岄柣鎰靛墯閸欏繘鏌ｉ弮鍌氬付鐎瑰憡绻冩穱濠囶敍濮橆剚鍊┑鈽嗗灠閿曨亪鐛弽銊︾秶闁诡垎灞惧枠婵犵數鍋涢悧濠囧垂瑜版帒绠憸鐗堝笚閺呮繈鏌嶈閸撴瑩鎮鹃悜鑺ュ亜闁惧繐婀遍敍婊堟⒑缂佹ɑ鈷愭俊鐐叉健瀹曘儵鍩€椤掑嫭鈷戦柛娑橈功閻﹪鏌ゅú璇茬仸妞ゃ垺宀搁弻鍡楊吋閸涱垼妲遍梻浣告啞缁诲倻鈧凹鍓熼敐鐐烘偐缂佹鍘?
 */
@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);
    private static final String DEFAULT_CURRENCY = "CNY";
    private static final String DIMENSION_INSTALLER = "installer";
    private static final String DIMENSION_DEALER = "dealer";

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

        return MoneyScaleUtil.keepTwoDecimals(order.getFeeAmount());
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
                    ? MoneyScaleUtil.keepTwoDecimals(adjusted)
                    : BigDecimal.ZERO;
        }

        BigDecimal fallback = safeAmount(order.getAmount())
                .subtract(safeEffectiveFee)
                .subtract(safeAmount(order.getPlanCost()));
        return fallback.compareTo(BigDecimal.ZERO) > 0
                ? MoneyScaleUtil.keepTwoDecimals(fallback)
                : BigDecimal.ZERO;
    }

    private void normalizeFinancialFieldsForDisplay(PaymentOrder order) {
        if (order == null || PaymentOrderStatus.PAID != order.getStatus()) {
            return;
        }

        BigDecimal effectiveFee = calculateEffectiveFeeAmount(order);
        BigDecimal effectiveProfit = calculateEffectiveProfitAmount(order, effectiveFee);
        order.setFeeAmount(effectiveFee);
        order.setPlanCost(order.getPlanCost() != null ? MoneyScaleUtil.keepTwoDecimals(order.getPlanCost()) : null);
        order.setProfitAmount(effectiveProfit);
        order.setInstallerAmount(order.getInstallerAmount() != null ? MoneyScaleUtil.keepTwoDecimals(order.getInstallerAmount()) : null);
        order.setDealerAmount(order.getDealerAmount() != null ? MoneyScaleUtil.keepTwoDecimals(order.getDealerAmount()) : null);
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
        return MoneyScaleUtil.keepTwoDecimals(remainingProfit);
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

    private static class DealerCommissionSlice {
        private final Long dealerId;
        private final String dealerCode;
        private final BigDecimal rate;
        private final BigDecimal amount;

        private DealerCommissionSlice(Long dealerId, String dealerCode, BigDecimal rate, BigDecimal amount) {
            this.dealerId = dealerId;
            this.dealerCode = dealerCode;
            this.rate = rate != null ? rate : BigDecimal.ZERO;
            this.amount = amount != null ? amount : BigDecimal.ZERO;
        }

        public Long getDealerId() {
            return dealerId;
        }

        public String getDealerCode() {
            return dealerCode;
        }

        public BigDecimal getRate() {
            return rate;
        }

        public BigDecimal getAmount() {
            return amount;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean isSettledFlag(Integer value) {
        return value != null && value == 1;
    }

    private boolean isInstallerSettled(PaymentOrder order) {
        if (order == null) {
            return false;
        }
        if (order.getInstallerIsSettled() != null) {
            return isSettledFlag(order.getInstallerIsSettled());
        }
        return isSettledFlag(order.getIsSettled());
    }

    private boolean isDealerSettled(PaymentOrder order) {
        if (order == null) {
            return false;
        }
        if (order.getDealerIsSettled() != null) {
            return isSettledFlag(order.getDealerIsSettled());
        }
        return isSettledFlag(order.getIsSettled());
    }

    private boolean isSettledByDimension(PaymentOrder order, String dimension) {
        String normalized = normalizeDimension(dimension);
        if (DIMENSION_DEALER.equals(normalized)) {
            return isDealerSettled(order);
        }
        return isInstallerSettled(order);
    }

    private int getSettledValueByDimension(PaymentOrder order, String dimension) {
        return isSettledByDimension(order, dimension) ? 1 : 0;
    }

    private void markOrderSettledByDimension(PaymentOrder order, String dimension) {
        if (order == null) {
            return;
        }
        String normalized = normalizeDimension(dimension);
        if (DIMENSION_DEALER.equals(normalized)) {
            order.setDealerIsSettled(1);
        } else {
            order.setInstallerIsSettled(1);
        }
        // 兼容旧字段：只有两个维度都结算后才算整单结算
        order.setIsSettled(isInstallerSettled(order) && isDealerSettled(order) ? 1 : 0);
    }

    private String resolveSettlementDimension(String dimension, String installerCode, Long dealerId) {
        String normalized = normalizeDimension(dimension);
        if (normalized != null) {
            return normalized;
        }
        if (dealerId != null) {
            return DIMENSION_DEALER;
        }
        if (hasText(installerCode)) {
            return DIMENSION_INSTALLER;
        }
        return DIMENSION_INSTALLER;
    }

    private BillingDataScope resolveBillingDataScope(Long currentUserId, String installerCode, Long dealerId) {
        return resolveBillingDataScope(currentUserId, installerCode, dealerId, null);
    }

    private String normalizeDimension(String dimension) {
        if (!hasText(dimension)) {
            return null;
        }
        String normalized = dimension.trim().toLowerCase(Locale.ROOT);
        if (DIMENSION_INSTALLER.equals(normalized) || DIMENSION_DEALER.equals(normalized)) {
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

    private List<String> resolveDealerDeviceIdsByDealerId(Long dealerId) {
        if (dealerId == null) {
            return Collections.emptyList();
        }
        try {
            Dealer dealer = dealerRepository.selectById(dealerId);
            if (dealer == null || !hasText(dealer.getDealerCode())) {
                return Collections.emptyList();
            }
            List<String> ids = deviceDealerRepository.listDeviceIdsByDealerCode(dealer.getDealerCode());
            return ids != null ? ids : Collections.emptyList();
        } catch (Exception e) {
            log.debug("闂傚倷绀侀幖顐ゆ偖椤愶箑纾块柟缁㈠櫘閺佸淇婇妶鍛殶缁炬儳銈搁悡顐﹀炊閵娧€濮囬梺杞扮劍閿曘垽寮诲☉銏″亜闂佸灝顑愬Λ锕傛⒑閻熸壋鍋撻悢铚傛睏闂佸湱鎳撶€氫即銆佸☉妯锋斀闁归偊鍓氬▍妤€鈹戦悩顔肩伇婵炲绋戠叅闁靛牆顦闂佺懓顕慨鐢碘偓? dealerId={}, error={}", dealerId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<DealerCommissionSlice> resolveDealerCommissionSlices(PaymentOrder order) {
        if (order == null) {
            return Collections.emptyList();
        }
        BigDecimal dealerPoolAmount = safeAmount(order.getDealerAmount());
        BigDecimal dealerPoolRate = safeAmount(order.getDealerRate());
        if (dealerPoolAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return Collections.emptyList();
        }

        if (!hasText(order.getDeviceId())) {
            if (order.getDealerId() == null) {
                return Collections.emptyList();
            }
            return Collections.singletonList(
                    new DealerCommissionSlice(order.getDealerId(), order.getDealerCode(), dealerPoolRate, dealerPoolAmount)
            );
        }

        List<DeviceDealer> dealerChain;
        try {
            dealerChain = deviceDealerRepository.getDealerChainByDeviceId(order.getDeviceId());
        } catch (Exception e) {
            log.debug("闂傚倷绀侀幖顐ゆ偖椤愶箑纾块柟缁㈠櫘閺佸淇婇妶鍛殲濠殿垰銈搁弻鐔碱敍閸℃婀伴弽锛勭磽閸屾艾鈧绮堟担鐑樺床婵せ鍋撶€殿喓鍔戞慨鈧柕鍫濇噽閺屽牓姊虹化鏇炲⒉缂佸甯￠獮濠囨偐缂佹ê浠繛鎾村嚬閸ㄤ即鎮橀悩缁樼厪闁搞儯鍔岄悘鈺呮煙? deviceId={}, error={}", order.getDeviceId(), e.getMessage());
            dealerChain = Collections.emptyList();
        }

        if (dealerChain == null || dealerChain.isEmpty()) {
            if (order.getDealerId() == null) {
                return Collections.emptyList();
            }
            return Collections.singletonList(
                    new DealerCommissionSlice(order.getDealerId(), order.getDealerCode(), dealerPoolRate, dealerPoolAmount)
            );
        }

        BigDecimal totalSubRate = BigDecimal.ZERO;
        for (int i = 1; i < dealerChain.size(); i++) {
            BigDecimal subRate = dealerChain.get(i).getCommissionRate() != null
                    ? dealerChain.get(i).getCommissionRate() : BigDecimal.ZERO;
            totalSubRate = totalSubRate.add(subRate);
        }

        List<DealerCommissionSlice> slices = new ArrayList<>();
        for (int i = 0; i < dealerChain.size(); i++) {
            DeviceDealer node = dealerChain.get(i);
            BigDecimal chainRate = node.getCommissionRate() != null ? node.getCommissionRate() : BigDecimal.ZERO;
            if (i == 0) {
                chainRate = new BigDecimal("100").subtract(totalSubRate);
            }
            if (chainRate.compareTo(BigDecimal.ZERO) < 0) {
                chainRate = BigDecimal.ZERO;
            }
            BigDecimal actualAmount = MoneyScaleUtil.percentOf(dealerPoolAmount, chainRate);
            BigDecimal actualRate = MoneyScaleUtil.percentOf(dealerPoolRate, chainRate);
            slices.add(new DealerCommissionSlice(node.getDealerId(), node.getDealerCode(), actualRate, actualAmount));
        }
        return slices;
    }

    private DealerCommissionSlice resolveDealerCommissionSlice(PaymentOrder order, Long targetDealerId) {
        if (targetDealerId == null) {
            return null;
        }
        for (DealerCommissionSlice slice : resolveDealerCommissionSlices(order)) {
            if (slice.getDealerId() != null && targetDealerId.equals(slice.getDealerId())) {
                return slice;
            }
        }
        return null;
    }

    private void applyDealerCommissionSlice(List<PaymentOrder> orders, Long targetDealerId) {
        if (orders == null || orders.isEmpty() || targetDealerId == null) {
            return;
        }
        for (PaymentOrder order : orders) {
            DealerCommissionSlice slice = resolveDealerCommissionSlice(order, targetDealerId);
            if (slice != null) {
                order.setDealerId(slice.getDealerId());
                order.setDealerCode(slice.getDealerCode());
                order.setDealerRate(slice.getRate());
                order.setDealerAmount(slice.getAmount());
            } else {
                order.setDealerRate(BigDecimal.ZERO);
                order.setDealerAmount(BigDecimal.ZERO);
            }
        }
    }

    private void applyInstallerDealerScope(QueryWrapper<PaymentOrder> qw,
                                           BillingDataScope scope,
                                           List<String> dealerDeviceIds) {
        List<String> safeDealerDeviceIds = dealerDeviceIds != null ? dealerDeviceIds : Collections.emptyList();
        if (scope.useInstallerDealerOr) {
            final String installerCode = scope.installerCode;
            final Long dealerId = scope.dealerId;
            final List<String> finalDealerDeviceIds = safeDealerDeviceIds;
            if (!finalDealerDeviceIds.isEmpty()) {
                qw.and(wrapper -> wrapper
                        .eq("installer_code", installerCode)
                        .or()
                        .eq("dealer_id", dealerId)
                        .or()
                        .in("device_id", finalDealerDeviceIds)
                );
            } else {
                qw.and(wrapper -> wrapper
                        .eq("installer_code", installerCode)
                        .or()
                        .eq("dealer_id", dealerId)
                );
            }
            return;
        }

        if (hasText(scope.installerCode)) {
            qw.lambda().eq(PaymentOrder::getInstallerCode, scope.installerCode);
        }
        if (scope.dealerId != null) {
            final Long dealerId = scope.dealerId;
            final List<String> finalDealerDeviceIds = safeDealerDeviceIds;
            if (!finalDealerDeviceIds.isEmpty()) {
                qw.and(wrapper -> wrapper
                        .eq("dealer_id", dealerId)
                        .or()
                        .in("device_id", finalDealerDeviceIds)
                );
            } else {
                qw.lambda().eq(PaymentOrder::getDealerId, scope.dealerId);
            }
        }
    }

    private void applyInstallerDealerScope(QueryWrapper<PaymentOrder> qw, BillingDataScope scope) {
        applyInstallerDealerScope(qw, scope, Collections.emptyList());
    }


    /**
     * 闂傚倷绀侀崥瀣磿閹惰棄搴婇柤鑹扮堪娴滃綊鏌涢妷銏℃珕闁哥喐鍨块弻娑樷槈閸楃偞鐏嶇紓浣靛妽瀹€鎼佸蓟濞戙垺鍋勯梺鍨儛濡儲绻濋姀锝嗙【閻庢碍婢橀悾鐑芥晸閻樻彃宓嗛梺缁樻⒒閸庛倝鎳ｉ崶顒佲拺閻犲洠鈧啿鈷夐悗瑙勬礈閺佹悂宕氶幒妤佹優閻熸瑥瀚?
     * 闂傚倷鑳堕崕鐢稿疾濞戙垺鍋ら柕濞у嫭娈伴梺鍦檸閸犳宕?installerCode 闂傚倷绀侀幖顐ゆ偖椤愶箑纾块柟缁㈠櫘閺?payment_order 闂?
     */
    public Map<String, Object> getInstallerBillingSummary(Long currentUserId, Long installerId, String installerCode, Date startDate, Date endDate) {
        // 缂傚倷鑳堕搹搴ㄥ矗鎼淬劌绐楅柡宥庡幗閸嬧晛螖閿濆懎鏆欓悗姘槸椤法鎹勬笟顖氬壉濠电偛鎳庣换姗€寮婚敐澶娢╅柕澶堝労娴煎倸顪冮妶鍐ㄥ姎闁挎洦浜滈锝夋偨缁嬪じ绱堕梺鍛婃处閸撴岸鎮靛鍕閻庣數顭堥鎾剁磼閹绘帗鍋ラ柟?
        String effectiveInstallerCode = installerCode;
        
        // 闂傚倸鍊搁崐鎼佹偋韫囨稑纾婚柣鏃傗拡閺佸洭鏌熼梻瀵稿妽闁稿濞€閺屾盯鍩勯崘鐐暦闂佸摜濮撮幊姗€寮诲☉妯锋瀻婵☆垵顫夐幑锝夋⒑缁嬭法绠查柨鏇樺灩椤曪絾绻濆顑┿劎鎲告惔锝囦笉婵炲棙鎸婚悡鐘测攽閸屾凹妲风紒銊ㄥ吹缁辨帞鎷嬪畷鍥╃崲闂佽鍨伴崯鏉戠暦閻旂⒈鏁嗗ù锝堫嚃閸熲偓闂?
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
        
        // 闂傚倷绀侀幖顐︻敄閸涱垪鍋撳鐓庡缂佽鲸鎹囬獮妯兼嫚閼艰埖鎲伴梻渚€娼чˇ浠嬫偂閸儱鍚归柛鏇ㄥ灡閻撴稑顭跨捄鐑橆棏闁稿鎹囧畷锝嗗緞濡桨缂?
        QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();
        qw.lambda().eq(PaymentOrder::getStatus, PaymentOrderStatus.PAID);
        
        // 闂?installerCode 闂傚倷绀侀幖顐ゆ偖椤愶箑纾块柟缁㈠櫘閺?
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

        // 闂傚倷绀佸﹢閬嶁€﹂崼銉嬪洭骞庨崜鍨槹缁绘繈宕惰閻濇﹢姊洪崨濠勭畵閻庢凹鍘奸埢宥囦沪閻偄缍婇幃顏堝川椤栨稑浠归梻浣虹帛閻楁鍒掗幘宕囨殾婵娉涢獮銏＄箾閸℃ê鐏╅柣鎾茶兌缁辨捇宕掑▎鎺戝帯闂佸摜濮靛ú鐔笺€?
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
                code = "UNASSIGNED";
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
            BigDecimal orderAmount = MoneyScaleUtil.keepTwoDecimals(order.getAmount());
            BigDecimal insAmt = order.getInstallerAmount() != null ? MoneyScaleUtil.keepTwoDecimals(order.getInstallerAmount()) : BigDecimal.ZERO;
            BigDecimal dealerAmt = order.getDealerAmount() != null ? MoneyScaleUtil.keepTwoDecimals(order.getDealerAmount()) : BigDecimal.ZERO;
            BigDecimal planCost = MoneyScaleUtil.keepTwoDecimals(order.getPlanCost());
            boolean isSettled = isInstallerSettled(order);

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

        // 闂備浇宕甸崑鐐电矙韫囨稑纾块梺顒€绉撮崒銊╂煛瀹ュ骸浜濋柛鐔稿灴閺屾稑鈽夐崡鐐寸亶缂備降鍔嶅畝鎼佸蓟濞戙垺鍋勯梺鍨儛濡偤姊虹拠鈥虫灍婵炲弶绮庨崚?
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
            if (installerName == null && code != null && !"UNASSIGNED".equals(code)) {
                QueryWrapper<Installer> iqw = new QueryWrapper<>();
                iqw.lambda().eq(Installer::getInstallerCode, code);
                Installer installer = installerRepository.selectOne(iqw);
                if (installer != null) {
                    installerName = installer.getInstallerName();
                    stat.put("installerId", installer.getId());
                }
            }
            stat.put("installerName", installerName != null ? installerName : ("UNASSIGNED".equals(code) ? "UNASSIGNED" : "UNKNOWN"));
        }

        // 闂備浇宕垫慨宕囨閵堝洦顫曢柡鍥ュ灪閸嬧晛鈹戦悩瀹犲缂佲偓閸儲鐓冮悶娑掆偓鍏呭缂傚倸鍊哥粔鐢稿垂閸喚鏆︽慨妯挎硾閻撴盯鏌涘畝鈧崑鐔虹矆?= 闂傚倷绀侀幉锟犳偡椤栫偛鍨傚ù鍏兼綑閸ㄥ倿骞栫划瑙勵€嗘俊鎻掔墦閺屻劑寮撮悙娴嬪亾缁嬪簱鏋嶉柕濞垮剻?- 闂備浇宕甸崰宥囩矆娓氣偓楠炲﹨绠涢幘顖涙暊闂侀€炲苯澧撮柡灞剧洴閹垽鏌ㄧ€ｅ灚顥ｉ梻浣规偠閸斿本顨ラ幖浣哥叀?- 缂傚倸鍊搁崐椋庣矆娴ｇ儤宕叉慨妞诲亾鐎殿喓鍔戞慨鈧柕鍫濇噽閺屽牓姊虹化鏇炲⒉妞ゃ劌鎳橀幃妤冪磼濡偐顔?
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
     * 闂傚倷绀侀崥瀣磿閹惰棄搴婇柤鑹扮堪娴滃綊鏌涢妷銏℃珖缁炬儳銈搁悡顐﹀炊閵娧€濮囬梺杞扮劍閿曘垽寮诲☉銏″亜闂佸灝顑愬Λ銉︾節閵忥絾纭鹃悗姘緲閻ｇ兘鏁撻悩鎻掑祮闂佺粯姊婚崕銈夋嚕閸ヮ剚鈷戦悹鍥ｂ偓鍐测拤閻庤娲滈弫鎼佸礆閹烘鎯為悷娆忓椤?
     * 闂傚倷绀侀幖顐ょ矓閻戞枻缍栧璺猴功閺嗐倕霉閿濆拋娼熷ù婊冪秺閺岀喖骞嗚閺嗚鲸銇勯妶鍛殗闁哄矉缍侀敐鐐侯敆閳ь剚淇婃禒瀣厱闁冲搫鍊婚妴鎺楁煙閸欏灏︽鐐村浮瀵挳鎮滈崱姗嗘闂傚倷鑳堕崢褔銆冩惔銏㈩洸婵犲﹤瀚崣蹇涙煃閸濆嫬鈧绂嶉妶澶嬬厱闁靛绲芥俊钘夆攽?
     */
    public Map<String, Object> getDealerBillingSummary(Long currentUserId, String installerCode, Long dealerId, Date startDate, Date endDate) {
        BillingDataScope scope = resolveBillingDataScope(currentUserId, installerCode, dealerId, "dealer");

        List<String> dealerDeviceIds = scope.dealerId != null
                ? resolveDealerDeviceIdsByDealerId(scope.dealerId)
                : Collections.emptyList();

        QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();
        qw.lambda().eq(PaymentOrder::getStatus, PaymentOrderStatus.PAID);
        applyInstallerDealerScope(qw, scope, dealerDeviceIds);
        if (startDate != null) {
            qw.lambda().ge(PaymentOrder::getPaidAt, startDate);
        }
        if (endDate != null) {
            qw.lambda().le(PaymentOrder::getPaidAt, endDate);
        }

        List<PaymentOrder> orders = orderRepository.selectList(qw);

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
            String currency = normalizeCurrency(order.getCurrency());
            BigDecimal orderAmount = MoneyScaleUtil.keepTwoDecimals(order.getAmount());
            BigDecimal installerAmt = MoneyScaleUtil.keepTwoDecimals(order.getInstallerAmount());
            BigDecimal planCost = MoneyScaleUtil.keepTwoDecimals(order.getPlanCost());
            boolean isSettled = isDealerSettled(order);

            totalAmount = totalAmount.add(orderAmount);
            totalCost = totalCost.add(planCost);
            BigDecimal effectiveFeeAmount = calculateEffectiveFeeAmount(order);
            BigDecimal effectiveProfitAmount = calculateEffectiveProfitAmount(order, effectiveFeeAmount);
            totalProfitAmount = totalProfitAmount.add(effectiveProfitAmount);
            totalInstallerAmount = totalInstallerAmount.add(installerAmt);
            addCurrencyAmount(totalAmountByCurrency, currency, orderAmount);
            addCurrencyAmount(totalCostByCurrency, currency, planCost);
            addCurrencyAmount(totalProfitAmountByCurrency, currency, effectiveProfitAmount);
            addCurrencyAmount(totalInstallerAmountByCurrency, currency, installerAmt);
            if (order.getDeviceId() != null) {
                deviceIds.add(order.getDeviceId());
            }
            totalOrders++;

            List<DealerCommissionSlice> dealerSlices;
            if (scope.dealerId != null) {
                DealerCommissionSlice targetSlice = resolveDealerCommissionSlice(order, scope.dealerId);
                dealerSlices = targetSlice != null ? Collections.singletonList(targetSlice) : Collections.emptyList();
            } else {
                dealerSlices = resolveDealerCommissionSlices(order);
            }

            for (DealerCommissionSlice slice : dealerSlices) {
                Long sliceDealerId = slice.getDealerId();
                if (sliceDealerId == null) {
                    continue;
                }
                String key = String.valueOf(sliceDealerId);
                final Long finalDid = sliceDealerId;
                final String finalICode = order.getInstallerCode();
                final String finalDealerCode = slice.getDealerCode();

                Map<String, Object> stat = dealerStats.computeIfAbsent(key, k -> {
                    Map<String, Object> s = new HashMap<>();
                    s.put("dealerId", finalDid);
                    s.put("dealerCode", finalDealerCode);
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

                BigDecimal shareAmount = MoneyScaleUtil.keepTwoDecimals(slice.getAmount());
                stat.put("orderCount", (Integer) stat.get("orderCount") + 1);
                stat.put("totalAmount", ((BigDecimal) stat.get("totalAmount")).add(orderAmount));
                stat.put("dealerAmount", ((BigDecimal) stat.get("dealerAmount")).add(shareAmount));
                addCurrencyAmount(getOrCreateCurrencyMap(stat, "totalAmountByCurrency"), currency, orderAmount);
                addCurrencyAmount(getOrCreateCurrencyMap(stat, "dealerAmountByCurrency"), currency, shareAmount);

                if (isSettled) {
                    stat.put("settledAmount", ((BigDecimal) stat.get("settledAmount")).add(shareAmount));
                    addCurrencyAmount(getOrCreateCurrencyMap(stat, "settledAmountByCurrency"), currency, shareAmount);
                    totalSettledDealerAmount = totalSettledDealerAmount.add(shareAmount);
                    addCurrencyAmount(totalSettledDealerAmountByCurrency, currency, shareAmount);
                } else {
                    stat.put("unsettledAmount", ((BigDecimal) stat.get("unsettledAmount")).add(shareAmount));
                    addCurrencyAmount(getOrCreateCurrencyMap(stat, "unsettledAmountByCurrency"), currency, shareAmount);
                    totalUnsettledDealerAmount = totalUnsettledDealerAmount.add(shareAmount);
                    addCurrencyAmount(totalUnsettledDealerAmountByCurrency, currency, shareAmount);
                }

                totalDealerAmount = totalDealerAmount.add(shareAmount);
                addCurrencyAmount(totalDealerAmountByCurrency, currency, shareAmount);
            }
        }

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
     * 闂傚倷绀侀崥瀣磿閹惰棄搴婇柤鑹扮堪娴滃綊鏌涢妷銏℃珕濠殿垰銈搁弻鏇＄疀閺囩倫銏㈢磼閳ь剛鈧綆鍠楅悡娑㈡煕閺囥劌浜濋柟鐧哥悼缁辨帡顢欓懖鈺冾啋閻庤娲橀懝鎹愮亙闂佸憡娲嶉弬渚€宕戦幘璇茬妞ゆ棁濮ゅ▍鏍⒑閸撴彃浜栭柛銊︽そ瀵疇绠涢幘浣烘嚀椤劑宕橀鍛亾濡や降浜滈柡鍥舵線閹插墽鈧娲橀悷鈺呭箖椤旈敮鍋撻棃娑欐喐妞?
     * @param installerCode 闂備浇宕甸崰宥囩矆娓氣偓楠炲﹨绠涢幘顖涙暊闂侀€炲苯澧撮柡灞剧洴閹垽鏌ㄧ€ｅ灚顥犻梻浣告憸閸庢劙宕滈悢鐓庣畾鐎广儱妫涢悷鐟扳攽閻樻彃顏い锔规櫊濮婃椽宕ㄦ繝鍕櫧闂佹悶鍔岄悥濂稿春閳ь剚銇勯幒鍡椾壕濡炪伇鈧崑鎾剁磽?
     * @param dealerId 缂傚倸鍊搁崐椋庣矆娴ｇ儤宕叉慨妞诲亾鐎殿喓鍔戞慨鈧柕鍫濇噽閺屽牓姊洪崨濠勭煂闁伙附宀稿娲箰鎼达絺妲堥梺缁橆殔濡粓濡甸幇鏉胯摕闁靛濡囬崝鍨節閵忥絽鐓愮紒瀣尵缁?
     * @param startDate 闂佽瀛╅鏍窗閹烘纾婚柟鐐灱閺€鑺ャ亜閺冨倵鎷￠柛搴㈠姈娣囧﹪顢曢悢鍛婄彋閻?
     * @param endDate 缂傚倸鍊搁崐鐑芥倿閿曞倸绠伴悹鍥ф▕閻掕姤銇勯幇鍓佺暠缂侇偄绉归弻鏇熷緞濞戙垺顎嶉梺?
     */
    public List<Map<String, Object>> getOrderDetails(Long currentUserId, String installerCode, Long dealerId, Date startDate, Date endDate) {
        BillingDataScope scope = resolveBillingDataScope(currentUserId, installerCode, dealerId);
        List<String> dealerDeviceIds = scope.dealerId != null
                ? resolveDealerDeviceIdsByDealerId(scope.dealerId)
                : Collections.emptyList();

        QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();
        qw.lambda().eq(PaymentOrder::getStatus, PaymentOrderStatus.PAID);
        applyInstallerDealerScope(qw, scope, dealerDeviceIds);
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
        if (scope.dealerId != null && scope.showDealerInfo) {
            applyDealerCommissionSlice(orders, scope.dealerId);
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        String settledDimension = null;
        if (scope.showInstallerInfo && !scope.showDealerInfo) {
            settledDimension = DIMENSION_INSTALLER;
        } else if (!scope.showInstallerInfo && scope.showDealerInfo) {
            settledDimension = DIMENSION_DEALER;
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (PaymentOrder order : orders) {
            Map<String, Object> row = new HashMap<>();
            row.put("orderId", order.getOrderId());
            row.put("deviceId", order.getDeviceId());
            row.put("installerCode", order.getInstallerCode());
            row.put("installerId", order.getInstallerId());
            row.put("installerName", getInstallerName(order.getInstallerId()));
            row.put("dealerId", order.getDealerId());
            row.put("dealerCode", order.getDealerCode());
            row.put("dealerName", getDealerName(order.getDealerId()));
            row.put("productType", order.getProductType());
            row.put("productId", order.getProductId());
            row.put("amount", order.getAmount());
            row.put("currency", order.getCurrency());
            row.put("feeAmount", order.getFeeAmount());
            row.put("planCost", order.getPlanCost());
            row.put("profitAmount", order.getProfitAmount());
            row.put("commissionRate", scope.showDealerInfo ? order.getCommissionRate() : null);
            row.put("installerRate", scope.showInstallerInfo ? order.getInstallerRate() : null);
            row.put("installerAmount", scope.showInstallerInfo ? order.getInstallerAmount() : null);
            row.put("dealerRate", scope.showDealerInfo ? order.getDealerRate() : null);
            row.put("dealerAmount", scope.showDealerInfo ? order.getDealerAmount() : null);
            row.put("paymentMethod", order.getPaymentMethod());
            row.put("paidAt", order.getPaidAt() != null ? sdf.format(order.getPaidAt()) : "");
            row.put("refundAt", order.getRefundAt() != null ? sdf.format(order.getRefundAt()) : "");
            row.put("refundReason", order.getRefundReason());
            row.put("isSettled", settledDimension != null
                    ? getSettledValueByDimension(order, settledDimension)
                    : order.getIsSettled());
            result.add(row);
        }
        return result;
    }

    /**
     * 闂傚倷绀侀幉锛勬暜閹烘嚦娑樷攽鐎ｎ€儱顭块懜闈涘闁藉啰鍠栭弻鏇熷緞濡厧甯ラ梺鎼炲€曠€氫即骞冪憴鍕闂傚牊绋撴禒濂告倵鐟欏嫭绀堥柛鐘崇墪閻ｅ嘲顫濋鈺傛瀹曠喖鍩為幆褌澹?
     * 闂傚倷绀侀幖顐ょ矓閻戞枻缍栧璺猴功閺嗐倕霉閿濆拋娼熷ù婊冪秺閺岀喖骞嗚閺嗚鲸銇勯妶鍛殗闁哄矉缍侀敐鐐侯敆閳ь剚淇婃禒瀣厱闁冲搫鍊婚妴鎺楁煙閸欏灏︽鐐村浮瀵挳鎮滈崱姗嗘闂傚倷鑳堕崢褔銆冩惔銏㈩洸婵犲﹤瀚崣蹇涙煃閸濆嫬鈧绂嶉妶澶嬬厱闁靛绲芥俊钘夆攽椤斿搫鐏查柡?
     * - 缂傚倸鍊烽懗鑸靛垔鐎靛憡顫曢柡鍥ュ灩缁犳牕鈹戦悩鍙夋悙鐎?role=3)闂傚倷鐒︾€笛呯矙閹烘鍤岄柟瑙勫姂娴滃綊鏌＄仦璇插姎闁藉啰鍠栭弻鏇熺珶椤栨艾顏柣锝勭矙濮婃椽宕烽褎姣岄梺绋款儐閹瑰洭寮婚敓鐘查唶婵犲灚鍔栨瓏婵＄偑鍊栭幐鍝ョ礊婵犲倻鏆?
     * - 闂備浇宕甸崰宥囩矆娓氣偓楠炲﹨绠涢幘顖涙暊闂侀€炲苯澧撮柡?isInstaller=1)闂傚倷鐒︾€笛呯矙閹烘鍤岄柟瑙勫姂娴滃湱鎲搁弮鍫濈疇婵°倕鎳忛崵宥夋煏婢诡垰鍟紒鈺呮⒒娴ｈ銇熷ù婊勭矒椤㈡牠宕奸妷銉ユ優濠电姴锕ら崰姘跺疮閸涘瓨鐓曟俊銈呭暙娴犳粎鎲搁幍顔尖枅闁哄被鍊栧蹇涘Ω閿旇瀚芥繝鐢靛仜閻楀﹪宕硅ぐ鎺戠閻庯綆鍠栭～鍛存煟濡灝鐨烘い?
     * - 缂傚倸鍊搁崐椋庣矆娴ｇ儤宕叉慨妞诲亾鐎殿喓鍔戞慨鈧柕鍫濇噽閺?isDealer=1)闂傚倷鐒︾€笛呯矙閹烘鍤岄柟瑙勫姂娴滃湱鎲搁弮鍫濈疇婵°倕鎳忛崵宥夋煏婢诡垰鍟紒鈺呮⒒娴ｈ銇熷ù婊勭矒椤㈡牠宕奸妷銉ユ優濠电姴锕ら崰姘跺疮閸涘瓨鐓曟俊銈呭暙娴犳粎鎲搁幍顔尖枅闁哄被鍊栧蹇涘Ω閿旇瀚芥繝鐢靛仜閻楀﹪宕硅ぐ鎺戠閻庯綆鍠栭～鍛存煟濡灝鐨烘い?
     * - 闂傚倷绀侀幖顐﹀疮閵娾晛鍨傞柛婵嗗珋閿濆浼犻柕澶涚畱閺呮娊姊洪崨濠佺繁闁哥姵顨堥懞鍗烆潩閼哥數鍘遍梺缁樻閺€閬嵥夊鍛亾濞堝灝鏋ら柡浣筋嚙椤曪絾瀵奸幖顓熸櫖濠电姴锕ら幊鎰潖妤ｅ啯鈷掑ù锝呮憸濮樸劑鏌涚€ｎ偅宕岄柡灞剧洴閹垽鏌ㄧ€ｅ墎宸濈紓鍌欑瑜板宕￠崘鑼殾闁挎繂顦介弫宥嗙箾閹寸偠澹樼€殿喓鍔戝鍝劽虹拋宕囩泿濡炪倖鍨电€氼噣骞冮鈧弻鍡楊吋閸℃鐣梻浣告啞娓氭宕㈤懝鑸汗闁绘绮悡鐘诲级閸稑濡介柍閿嬪笚缁绘盯宕奸悢椋庝患闂佺懓鍢查澶嬫叏閳ь剟鏌ｅΟ鍨毢妞?OR闂傚倷绀侀幖顐λ囬锕€鐒垫い鎺嗗亾鐎殿喖鐖奸、?
     */
    public Map<String, Object> listOrders(Long currentUserId, Integer page, Integer size, String installerCode, Long dealerId, 
                                          String deviceId, String status, Date startDate, Date endDate) {
        // 闂傚倷绀侀幖顐ょ矓閻戞枻缍栧璺猴功閺嗐倕霉閿濆拋娼熷ù婊冪秺閺岀喖骞嗚閺嗚鲸銇勯妶鍛殗闁哄矉缍侀敐鐐侯敆閳ь剚淇婃禒瀣厱闁冲搫鍊婚妴鎺楁煙閸欏灏︽鐐村浮瀵挳鎮滈崱姗嗘缂傚倷鑳堕搹搴ㄥ矗鎼淬劌绐楅柡宥庡幗閸嬧晠鏌ｉ幇闈涘濞存嚎鍊濋弻娑㈠Ψ閿濆懎顬夋繝娈垮枔閸ㄤ粙寮婚埄鍐ㄧ窞闁糕€崇箰娴滈箖鏌涘▎蹇ｆ▓闁?
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
                // 缂傚倸鍊烽懗鑸靛垔鐎靛憡顫曢柡鍥ュ灩缁犳牕鈹戦悩鍙夋悙鐎瑰憡绻冩穱濠囶敍濮橆厽鍎撻柣鐘辩劍瑜板啴婀侀梺缁橈供閸犳牠宕濆鍫熺厽闁瑰灝瀚弧鈧梺璇″灠濞层劑鍩€椤掑﹦绉甸柛瀣閵嗗倿寮婚妷锔惧幐闂佸壊鍋呯换宥呂ｉ崗绗轰簻闁规儳纾粔铏光偓瑙勬穿缁绘繂顕ｉ幘顕呮晜闁糕剝鐟Σ宄扳攽閻愭潙鐏﹂柟绋挎憸缁棃鎮烽懜顑藉亾閹烘绀堝ù锝囨嚀閺嗩偅绻涙潏鍓ф偧妞ゎ厼鐗忛幑銏＄瑹閳ь剟寮诲☉銏犵闁规儳鍟挎慨鍝ョ磽娴ｇ瓔鍤欓柛鐕佸亰閳?
                boolean isAdmin = currentUser.getRole() != null && currentUser.getRole() == 3;
                
                if (!isAdmin) {
                    boolean isInstaller = currentUser.getIsInstaller() != null && currentUser.getIsInstaller() == 1 && currentUser.getInstallerId() != null;
                    boolean isDealer = currentUser.getIsDealer() != null && currentUser.getIsDealer() == 1 && currentUser.getDealerId() != null;
                    
                    // 闂備浇宕甸崰宥囩矆娓氣偓楠炲﹨绠涢幘顖涙暊闂侀€炲苯澧撮柡灞剧洴閹垽鏌ㄧ€ｅ灚顥ｉ柣鐔哥矋婵℃悂宕堕妸銉︾暠闂備礁鍚嬫禍浠嬪磿閹惰棄鐭楅柍褜鍓熷鍝劽虹拋宕囩泿濡炪倖鍨甸悧鎾崇暦闂堟侗鐓ラ柛鎰劤閺呯娀姊洪崨濠庢畼闁稿绋栭妵鎰版偐缂佹鍘鹃柡澶婄墑閸斿秹鍩涢幒鎾剁闁割偆鍠庨悘鈺呮煙瀹勯偊鍎斿┑顔瑰亾闂佺粯顭堢亸娆撍?
                    if (isInstaller) {
                        Installer installer = installerRepository.selectById(currentUser.getInstallerId());
                        if (installer != null && installer.getInstallerCode() != null) {
                            effectiveInstallerCode = installer.getInstallerCode();
                            log.info("闂備浇宕甸崰宥囩矆娓氣偓楠炲﹨绠涢幘顖涙暊闂侀€炲苯澧撮柡灞剧洴閹垽鏌ㄧ€ｅ灚锟ラ梻浣芥〃闂勫秹宕愬┑瀣祦閻庯綆鍠楅悡銉╂倵閿濆骸澧€殿喓鍔戦弻锝夋偐閸欏鍋嶉梺鎼炲妼閵堟悂銆佸Ο瑁や汗闁圭儤鍨归、鍛存⒑閸濆嫭宸濋柛瀣洴閹ê鈻庨幘鏉戜哗? userId={}, installerCode={}", currentUserId, effectiveInstallerCode);
                        }
                    }
                    
                    // 缂傚倸鍊搁崐椋庣矆娴ｇ儤宕叉慨妞诲亾鐎殿喓鍔戞慨鈧柕鍫濇噽閺屽牓姊虹化鏇炲⒉妞ゃ劌鎳庨埥澶庮樄闁哄被鍊楅埀顒€婀辨慨纾嬵暱闂備胶绮幖顐ゆ崲濠靛绠氶柛銉㈡杹閸嬫捇鏁愭惔鈥崇濠电偛鐗婃竟鍡涘焵椤掆偓濠€閬嶁€﹂崼婵堟殾妞ゆ帒瀚惌妤冩喐閺冨牆绠犻柡鍥ュ灩缁秹鏌涚仦鍓с€掗柡鍡欏█閺岋綁鎮╅柆宥嗩€栭梺鎼炲妿閹虫捇鎮?
                    if (isDealer) {
                        effectiveDealerId = currentUser.getDealerId();
                        log.info("缂傚倸鍊搁崐椋庣矆娴ｇ儤宕叉慨妞诲亾鐎殿喓鍔戞慨鈧柕鍫濇噽閺屽牓姊虹化鏇炲⒉闁荤噦绠撳瀹犵疀濞戞瑧鍘甸梺鍦檸閸犳寮柆宥嗙厽闁瑰灝瀚壕鍧楁煙妞嬪骸鈻堝┑顔瑰亾闂佹寧绻傛鎼佸箖閸涘瓨鈷戦柛娑橆煬濞堟洘绻涢崣澶屽⒌鐎规洖缍婇、鏃堝幢濡桨绮? userId={}, dealerId={}", currentUserId, effectiveDealerId);
                    }
                    
                    // 闂傚倷绀侀幖顐﹀疮閵娾晛鍨傞柛婵嗗珋閿濆浼犻柕澶涚畱閺呮娊姊洪崨濠佺繁闁哥姵顨堥懞鍗烆潩閼哥數鍘遍梺缁樻閺€閬嵥夊鍛亾濞堝灝鏋ら柡浣筋嚙椤曪絾瀵奸幖顓熸櫖濠电姴锕ら幊鎰潖妤ｅ啯鈷掑ù锝呮憸濮樸劑鏌涚€ｎ偅宕岄柡灞剧洴閹垽鏌ㄧ€ｅ墎宸濈紓鍌欐祰妞村摜鎹㈤崼婢稒绗熼埀顒勫春閳ь剚銇勯幒鎴濃偓褰掑疮閸濆嫧妲堥柟鎹愬煐閹兼劙姊绘担鍛婃儓妞わ富鍋婇崺鈧い鎺嗗亾鐎殿喖鐖奸、?
                    needOrCondition = isInstaller && isDealer;
                }
            }
        }
        
        QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();
        
        // 婵犵數濮烽。浠嬪焵椤掆偓閸熷潡鍩€椤掆偓缂嶅﹪骞冨Ο璇茬窞闁归偊鍓涢惈鍕⒑闂堟稓绠氶柛鎾寸箘閻熝囨⒑閼姐倕鏋嶇紒鈧笟鈧獮濠呯疀閹绢垱鏁犻梺閫炲苯澧撮柡灞剧洴閹垽鏌ㄧ€ｅ灚顥ｉ柣搴㈩問閸犳岸寮拠宸殨濞村吋鎯岄弫宥嗙節婵犲倹鍣芥慨濠囩畺濮婄粯鎷呴崫銉︾殤闂佺顑嗛幑鍥蓟濞戙垺鍋勯梺鍨儜缁便劎绱撴担浠嬪摵缂佽鍟撮獮蹇涘川椤栨稑纾梺闈涱煭闂勫嫬鈻撻、绀￠梻鍌欑閹碱偊藝椤愶箑鐒垫い鎺嗗亾鐎殿喖鐖奸、?
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

        // 闂傚倷绀侀幉锛勬暜閹烘嚦娑樷攽鐎ｎ€儱顭块懜闈涘闁藉啰鍠栭弻鏇熷緞濡厧甯ラ梺?
        int offset = (page - 1) * size;
        qw.last("LIMIT " + offset + ", " + size);
        List<PaymentOrder> list = orderRepository.selectList(qw);
        for (PaymentOrder order : list) {
            normalizeFinancialFieldsForDisplay(order);
        }

        // 闂傚倷绀侀幖顐ゆ偖椤愶箑纾块柟缁㈠櫘閺佸淇婇妶鍛櫣缂佺姵鍨圭槐鎾存媴閼测剝鍨甸埢?
        QueryWrapper<PaymentOrder> countQw = new QueryWrapper<>();
        // 婵犵數濮烽。浠嬪焵椤掆偓閸熷潡鍩€椤掆偓缂嶅﹪骞冨Ο璇茬窞闁归偊鍓涢惈鍕⒑闂堟稓绠氶柛鎾寸箘閻熝囨⒑閼姐倕鏋嶇紒鈧笟鈧獮濠呯疀閹绢垱鏁犻梺閫炲苯澧撮柡灞剧洴閹垽鏌ㄧ€ｅ灚顥ｉ柣搴㈩問閸犳岸寮拠宸殨濞村吋鎯岄弫宥嗙節婵犲倹鍣芥慨濠囩畺濮婄粯鎷呴崫銉︾殤闂佺顑嗛幑鍥蓟濞戙垺鍋勯梺鍨儜缁便劎绱撴担浠嬪摵缂佽鍟撮獮蹇涘川椤栨稑纾梺闈涱煭闂勫嫬鈻撻、绀￠梻鍌欑閹碱偊藝椤愶箑鐒垫い鎺嗗亾鐎殿喖鐖奸、?
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
     * 闂備浇宕垫慨鎶芥倿閿曗偓椤灝螣閼测晝顦悗骞垮劚椤︿即鎮炴繝姘厓鐟滄粓宕滃▎鎾崇厺?
     * @param orderId 闂備浇宕垫慨鎶芥⒔瀹ュ鍨傞柣鐔稿閺嗭箓鏌ｉ幋鐘虫嚈
     * @param reason 闂傚倸鍊风欢锟犲磻閳ь剟鏌涚€ｎ偅灏扮紒缁樼洴瀹曞崬螣閸濆嫬袘闂佹眹鍩勯崹濂稿磻婵犲倻鏆?
     */
    public Map<String, Object> listOrders(Long currentUserId, Integer page, Integer size, String dimension,
                                          String installerCode, Long dealerId, String deviceId,
                                          String status, Date startDate, Date endDate) {
        BillingDataScope scope = resolveBillingDataScope(currentUserId, installerCode, dealerId, dimension);
        List<String> dealerDeviceIds = scope.dealerId != null
                ? resolveDealerDeviceIdsByDealerId(scope.dealerId)
                : Collections.emptyList();

        PaymentOrderStatus effectiveStatus = PaymentOrderStatus.PAID;
        if (hasText(status)) {
            PaymentOrderStatus statusEnum = PaymentOrderStatus.fromCode(status);
            if (statusEnum != null) {
                effectiveStatus = statusEnum;
            }
        }

        QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();
        applyInstallerDealerScope(qw, scope, dealerDeviceIds);
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
        if (scope.dealerId != null && scope.showDealerInfo) {
            applyDealerCommissionSlice(list, scope.dealerId);
        }
        maskOrderCommissionFields(list, scope.showInstallerInfo, scope.showDealerInfo);
        String normalizedDimension = normalizeDimension(dimension);
        if (normalizedDimension != null) {
            for (PaymentOrder order : list) {
                order.setIsSettled(getSettledValueByDimension(order, normalizedDimension));
            }
        }

        QueryWrapper<PaymentOrder> countQw = new QueryWrapper<>();
        applyInstallerDealerScope(countQw, scope, dealerDeviceIds);
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
            throw new RuntimeException("Order not found");
        }
        if (order.getStatus() != PaymentOrderStatus.PAID) {
            throw new RuntimeException("Only paid orders can be refunded");
        }
        order.setStatus(PaymentOrderStatus.REFUNDED);
        order.setRefundAt(new Date());
        order.setRefundReason(reason);
        order.setUpdatedAt(new Date());
        orderRepository.updateById(order);
        log.info("閻犱焦婢樼紞宥夋焻閳ь剙鈻庨悙顒€鐏囬柛? orderId={}, reason={}", orderId, reason);
    }

    // Billing detail Excel headers
    private static final String[] BILLING_DETAIL_HEADERS = {
            "Order ID", "Device ID", "Online Country", "Installer Code", "Installer Name", "Dealer ID", "Dealer Name",
            "Plan Name", "Product Type", "Amount", "Currency", "Payment Method", "Paid At",
            "Fee Amount", "Plan Cost", "Profit Amount", "Installer Amount", "Dealer Amount", "Settled"
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
     * 闂備浇顕уù鐑藉极閹间礁鍌ㄧ憸鏂跨暦閻㈠壊鏁囬柕蹇曞Х椤ρ囨⒑閸涘﹣绶遍柛妯绘倐瀹曟垿骞樼拠鑼槯闂佺绻掗埛鍫濐焽閻樼數纾介柛灞剧懅椤︼箓鏌熼崫銉э紡cel
     * 婵犵數濮烽。浠嬪焵椤掆偓閸熷潡鍩€椤掆偓缂嶅﹪骞冨Ο璇茬窞闁归偊鍓氶悗顒勬⒑缂佹﹩娈旈柣妤€妫涚划濠囨晝閸屾稑浠遍梺鍦劋閸ㄥ爼宕甸埀顒傜磽?婵犵數鍋為崹鍫曞箰缁嬫５娲晝閳ь剟鈥﹂崶顒夋晬闁绘劕鐡ㄥ▍鏍煟韫囨洖浠╂俊顐㈠閹绻濆顓犲幍闁诲孩绋掗敋濠殿喖娲﹂妵鍕晜閽樺鍤嬬紓浣割儏椤︾敻骞冮鍩跺洭鎮楁刊寮嗛梻鍌欑閹碱偊宕愮粙妫垫椽鏁愰崨顏呯€婚梺闈涚箞閸婃鐣垫笟鈧弻銈夊箹娴ｈ閿梺鎰佷簽閺佸寮诲☉銏犲唨鐟滃酣宕冲ú顏呯厱闁靛瀵岄崝鐢秔
     * 闂傚倷绀侀幖顐λ囬鐐茬柈闁哄鍩堥悗鍫曟煣韫囨凹娼愰悗姘哺閹鈽夊▍铏灴閿濈偤寮介鐔哄弳濠电偞鍨堕悷銉╁传閻戞绠剧痪鏉垮綁缁ㄧ晫绱掗纰辩吋闁轰礁绉瑰畷鐔碱敆閳ь剟鍩€椤掑倸浠遍柡灞剧☉閳藉鈻嶉褌娴烽柕鍥ㄥ姌椤﹀綊鏌熼搹顐疁闁圭锕ュ鍕熼悜姗嗗晫闂傚倷绀侀幉锟犲垂閻撳寒娴栭柕濞у懐鐣堕悗鍏夊亾闁告洦鍓欏▓婊冾渻閵堝懐绠伴悗姘煎枤缁骞掑Δ浣哄幈闂婎偄娲﹂幐楣冨几閸愵煁褰掓偐閼碱剙鈪甸梺璇″灠閸熸潙鐣烽悢纰辨晢濞达綀顕栭崯鈧梻?
     * @return Map闂傚倷绀侀幉锟犳偋閺囥垹绠犻幖娣妼缁?"isZip" (boolean) 闂?"data" (byte[])
     */
    public Map<String, Object> exportBillingDetailExcel(Long currentUserId, String dimension, String installerCode,
                                                        Long dealerId, String deviceId, Date startDate,
                                                        Date endDate) throws IOException {
        BillingDataScope scope = resolveBillingDataScope(currentUserId, installerCode, dealerId, dimension);
        List<String> dealerDeviceIds = scope.dealerId != null
                ? resolveDealerDeviceIdsByDealerId(scope.dealerId)
                : Collections.emptyList();

        QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();
        qw.lambda().eq(PaymentOrder::getStatus, PaymentOrderStatus.PAID);
        applyInstallerDealerScope(qw, scope, dealerDeviceIds);
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
        if (scope.dealerId != null && scope.showDealerInfo) {
            applyDealerCommissionSlice(orders, scope.dealerId);
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
        List<String> dealerDeviceIds = scope.dealerId != null
                ? resolveDealerDeviceIdsByDealerId(scope.dealerId)
                : Collections.emptyList();
        
        log.info("闂佽瀛╅鏍窗閹烘纾婚柟鐐灱閺€鑺ャ亜閺冨倵鎷￠柛搴＄箲閵囧嫰寮撮～顓熷枤閻庤娲橀悷鈺佺暦椤愶箑绀嬫い鎰╁灮椤戝牓姊绘担渚劸闁挎洏鍊濆畷銉р偓锝庘偓顓ㄧ秮楠炲鎮╅悽纰夌床闂備礁鎲＄划搴ㄣ€冨鎶弆: currentUserId={}, installerCode={}, dealerId={}, deviceId={}, startDate={}, endDate={}", 
                currentUserId, scope.installerCode, scope.dealerId, deviceId, startDate, endDate);

        QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();
        qw.lambda().eq(PaymentOrder::getStatus, PaymentOrderStatus.PAID);
        applyInstallerDealerScope(qw, scope, dealerDeviceIds);
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
        if (scope.dealerId != null && scope.showDealerInfo) {
            applyDealerCommissionSlice(orders, scope.dealerId);
        }
        log.info("闂傚倷绀侀幖顐ゆ偖椤愶箑纾块柟缁㈠櫘閺佸淇婇妶鍛櫣缂佲偓閸℃稒鐓涘璺猴功娴犮垽鎮介婊冣枅闁哄本绋掔换婵嬪磼濮橆厼鈧垰鈹戦垾鍐茬骇闁搞劌婀卞Σ鎰板箳濡も偓楠炪垺鎱ㄥΟ鐓庡付闁诡喖鎳樺娲川婵犲孩鐣峰┑鐐插级閿曘垹顕? {}", orders.size());

        Map<String, Object> result = new HashMap<>();

        if (orders.size() <= EXCEL_BATCH_SIZE) {
            // 闂傚倷绀侀幉锟犮€冮崱妞曟椽骞嬮敂鐣屽帓濠电儑绲鹃幃鐚歟l闂傚倷绀侀幖顐﹀磹缁嬫５娲晲閸涱亝鐎?
            byte[] excelData = createBillingDetailExcel(orders, null, scope.showInstallerInfo, scope.showDealerInfo);
            result.put("isZip", false);
            result.put("data", excelData);
            log.info("Billing detail excel generated successfully");
        } else {
            // 闂傚倷绀侀幉锛勬暜閹烘嚦娑樷槈濞嗘劖鐝烽梺鍝勭▉閸樿偐绮婚妷鈺傜厵闁诡垎鍐╂瘣闂佷紮绠戦悥濂稿蓟閻旇　鍋撳☉娅偐妲愬顤?
            byte[] zipData = createBillingDetailZip(orders, scope.showInstallerInfo, scope.showDealerInfo);
            result.put("isZip", true);
            result.put("data", zipData);
            log.info("閻庣數鍘ч崵顓犳嫻閿曗偓瀹曠喖寮版惔锝囩煄ZIP闁瑰瓨鍔曟慨? 闁哄倸娲ｅ▎銏ゅ极?{}", (orders.size() + EXCEL_BATCH_SIZE - 1) / EXCEL_BATCH_SIZE);
        }

        return result;
    }

    /**
     * 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼幑鎰靛殭缂佲偓閸岀偞鐓曟繛鎴濆船楠炴劙鏌涚€ｎ偅宕岀€规洘顨婂畷妤冨枈鏉堛劎绋戠紓鍌氬€搁崐椋庣矆娓氣偓楠炴劗妲愭繅銆唋
     */
    private byte[] createBillingDetailExcel(List<PaymentOrder> orders, String sheetName) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(sheetName != null ? sheetName : "Billing Detail");

            // 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼幑鎰靛殭闁告劏鍋撻梻浣规偠閸庢椽宕滈敃鍌氭辈妞ゆ牜鍋為悡娑氣偓骞垮劚閹冲骸危閼姐倗纾?
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

            // 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼幑鎰靛殭闁哄绶氶弻锝呂旈埀顒勬偋閸℃瑧鐭堥柨鏇炲€归悡娑氣偓骞垮劚閹冲骸危閼姐倗纾?
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            // 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼崜褏甯涢柣鎾冲€块弻鐔告綇閹呮В闂佸搫顦板浠嬪蓟閳ユ剚鍚嬮柛娑卞幗浜涚紓?
            CellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.setBorderBottom(BorderStyle.THIN);
            moneyStyle.setBorderTop(BorderStyle.THIN);
            moneyStyle.setBorderLeft(BorderStyle.THIN);
            moneyStyle.setBorderRight(BorderStyle.THIN);
            DataFormat format = workbook.createDataFormat();
            moneyStyle.setDataFormat(format.getFormat("#,##0.00"));

            // 闂傚倷绀侀幉锟犲礉閺嶎厽鍋￠柕澶嗘櫅閻鏌涢埄鍐槈闁告劏鍋撻梻浣规偠閸庢椽宕滈敃鍌氭辈妞ゆ牜鍋為崑?
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < BILLING_DETAIL_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(BILLING_DETAIL_HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // 闂傚倷绀侀幉锟犲礉閺嶎厽鍋￠柕澶嗘櫅閻鏌涢埄鍐槈闁哄绶氶弻锝呂旈埀顒勬偋閸℃瑧鐭堥柨鏇炲€归崑?
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            int rowNum = 1;
            for (PaymentOrder order : orders) {
                Row row = sheet.createRow(rowNum++);
                int col = 0;

                // 闂備浇宕垫慨鎶芥⒔瀹ュ鍨傞柣鐔稿閺嗭箓鏌ｉ弮鍌氬付闁?
                createTextCell(row, col++, order.getOrderId(), dataStyle);
                // 闂備浇宕垫慨鎶芥倿閿曞倸纾块柟璺哄閸ヮ剚鍋犲☉?
                createTextCell(row, col++, order.getDeviceId(), dataStyle);
                // 婵犵數鍋為崹鍫曞箰閹间焦鏅濋柨鏇炲€搁崥褰掑箹濞ｎ剙濡奸柣鎰躬閺岋箑螣娓氼垱鈻撻梺鍝ュ暱閸?
                createTextCell(row, col++, order.getOnlineCountry(), dataStyle);
                // 闂備浇宕甸崰宥囩矆娓氣偓楠炲﹨绠涢幘顖涙暊闂侀€炲苯澧撮柡灞剧洴閹垽鏌ㄧ€ｅ灚顥犻梻浣告憸閸庢劙宕滈悢鐓庣畾?
                createTextCell(row, col++, order.getInstallerCode(), dataStyle);
                // 闂備浇宕甸崰宥囩矆娓氣偓楠炲﹨绠涢幘顖涙暊闂侀€炲苯澧撮柡灞剧洴閹垽鏌ㄧ€ｅ灚顥ｉ梻浣筋嚃閸犳煤濠婂懎鍨?
                String installerName = getInstallerName(order.getInstallerId());
                createTextCell(row, col++, installerName, dataStyle);
                // 缂傚倸鍊搁崐椋庣矆娴ｇ儤宕叉慨妞诲亾鐎殿喓鍔戞慨鈧柕鍫濇噽閺屽牓姊洪崨濠勭煂闁?
                createTextCell(row, col++, order.getDealerId() != null ? String.valueOf(order.getDealerId()) : "", dataStyle);
                // 缂傚倸鍊搁崐椋庣矆娴ｇ儤宕叉慨妞诲亾鐎殿喓鍔戞慨鈧柕鍫濇噽閺屽牓姊虹化鏇炲⒉妞ゃ劌妫濆畷鎰暦閸モ晝锛?
                String dealerName = getDealerName(order.getDealerId());
                createTextCell(row, col++, dealerName, dataStyle);
                // 婵犵數濞€濞佳囧磹瑜版帇鈧啯绻濋崶浣告喘閹晫绮欓懗顖呮洟鎮楅獮鍨姎婵?
                String productName = getProductName(order.getProductId());
                createTextCell(row, col++, productName, dataStyle);
                // 婵犵數濞€濞佳囧磹瑜版帇鈧啯绻濋崶浣告喘閹粙鎮介棃娑欐闂佽崵濮村ú鈺冧焊濞嗘挸鐒?
                createTextCell(row, col++, order.getProductType(), dataStyle);
                // 闂傚倷娴囬妴鈧柛瀣尰閵囧嫰寮介妸褎鍣柣銏╁灡閻╊垰顫忓ú顏勭煑濠㈣泛锕︽导灞筋渻?
                createMoneyCell(row, col++, order.getAmount(), moneyStyle);
                // 闂傚倷娴囬妴鈧柛瀣尰閵囧嫰寮介妸褎鍣柣銏╁灡閻╊垶骞冩禒瀣垫晬婵炲棙甯掗崢鈥愁渻?
                createTextCell(row, col++, order.getCurrency(), dataStyle);
                // 闂傚倷娴囬妴鈧柛瀣尰閵囧嫰寮介妸褎鍣柣銏╁灡閻╊垰顫忓ú顏嶆晝闁挎繂鎳庨幗鍨箾?
                createTextCell(row, col++, order.getPaymentMethod(), dataStyle);
                // 闂傚倷娴囬妴鈧柛瀣尰閵囧嫰寮介妸褎鍣柣銏╁灡閻╊垶寮婚敓鐘茬闁靛ě鍐幗婵?
                createTextCell(row, col++, order.getPaidAt() != null ? sdf.format(order.getPaidAt()) : "", dataStyle);
                // 闂傚倷绀佺紞濠傤焽瑜旈、鏍炊椤掍礁浠洪梺鐓庮潟閸婃牜鈧?
                createMoneyCell(row, col++, order.getFeeAmount(), moneyStyle);
                // 婵犵數濞€濞佳囧磹瑜版帇鈧啯绻濋崶浣告喘閹晫绮欑捄顭戝悈闂備礁缍婇崑濠囧储娴犲绠?
                createMoneyCell(row, col++, order.getPlanCost(), moneyStyle);
                // 闂傚倷绀侀幉锟犳偡椤栫偛鍨傚ù鍏兼綑閸ㄥ倿骞栧ǎ顒€濡肩紒鈧崱娑欑厓闁告繂瀚埀顒冩硶濡?
                createMoneyCell(row, col++, order.getProfitAmount(), moneyStyle);
                // 闂備浇宕甸崰宥囩矆娓氣偓楠炲﹨绠涢幘顖涙暊闂侀€炲苯澧撮柡灞剧洴閹垽鏌ㄧ€ｅ灚顥ｉ梻浣规偠閸斿本顨ラ幖浣哥叀?
                createMoneyCell(row, col++, order.getInstallerAmount(), moneyStyle);
                // 缂傚倸鍊搁崐椋庣矆娴ｇ儤宕叉慨妞诲亾鐎殿喓鍔戞慨鈧柕鍫濇噽閺屽牓姊虹化鏇炲⒉妞ゃ劌鎳橀幃妤冪磼濡偐顔?
                createMoneyCell(row, col++, order.getDealerAmount(), moneyStyle);
                // 闂佽姘﹂～澶愭偤閺囩姳鐒婃繛鍡樺灥閸ㄦ繈鏌曟繛褍鎳嶇粭?
                boolean settled = isInstallerSettled(order) && isDealerSettled(order);
                createTextCell(row, col++, settled ? "Yes" : "No", dataStyle);
            }

            // 闂傚倷鑳堕崢褔銆冩惔銏㈩洸婵犲﹤瀚崣蹇涙煃閸濆嫬鏆為悗姘煎墴閺屾盯骞囬妸锔界彆濠电偛鐗嗛…鐑藉蓟濞戙垹绠抽柟鍨暞閻ｈ泛顪?
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
     * 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼幑鎰靛殭缂佲偓閸岀偞鐓曟繛鎴濆船楠炴劙鏌涚€ｎ偅宕岀€规洘顨婂畷妤冨枈鏉堛劎绋戠紓鍌氬€搁崐椋庣矆娓氣偓楠炴鍩￠¨鍫曟⒒娴ｇ懓顕滅紒瀣灴閹囧幢濞嗗苯浜鹃柛顭戝亾閼拌法鈧娲忛崕鐢搞€侀弴銏狀潊闁冲搫鍊甸弻銈呪攽閻愭潙鐏﹂柟绋垮⒔閳ь剚姘ㄩ崝鏀僥l闂傚倷绀侀幖顐﹀磹缁嬫５娲晲閸涱亝鐎婚梺闈涚箞閸婃牠寮?
     */
    private byte[] createBillingDetailZip(List<PaymentOrder> orders) throws IOException {
        ByteArrayOutputStream zipOut = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(zipOut)) {
            int totalFiles = (orders.size() + EXCEL_BATCH_SIZE - 1) / EXCEL_BATCH_SIZE;
            for (int i = 0; i < totalFiles; i++) {
                int fromIndex = i * EXCEL_BATCH_SIZE;
                int toIndex = Math.min(fromIndex + EXCEL_BATCH_SIZE, orders.size());
                List<PaymentOrder> batch = orders.subList(fromIndex, toIndex);

                String fileName = String.format("billing-detail-%d-%d.xlsx", fromIndex + 1, toIndex);
                byte[] excelData = createBillingDetailExcel(batch, "Billing Detail");

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

                String fileName = String.format("billing-detail-%d-%d.xlsx", fromIndex + 1, toIndex);
                byte[] excelData = createBillingDetailExcel(batch, "Billing Detail", showInstallerInfo, showDealerInfo);

                ZipEntry entry = new ZipEntry(fileName);
                zos.putNextEntry(entry);
                zos.write(excelData);
                zos.closeEntry();
            }
        }
        return zipOut.toByteArray();
    }

    /**
     * 闂傚倷绀侀崥瀣磿閹惰棄搴婇柤鑹扮堪娴滃綊鏌涢妷銏℃珕闁哥喐鍨块弻娑樷槈閸楃偞鐏嶇紓浣靛妽瀹€鎼佸蓟濞戙垺鍋勯梺鍨儛濡偤姊虹拠鈥虫灍婵炲弶绮庨崚?
     */
    private String getInstallerName(Long installerId) {
        if (installerId == null) return "";
        Installer installer = installerRepository.selectById(installerId);
        return installer != null ? installer.getInstallerName() : "";
    }

    /**
     * 闂傚倷绀侀崥瀣磿閹惰棄搴婇柤鑹扮堪娴滃綊鏌涢妷锝呭闂傚嫬瀚穱濠囧Χ閸涱喖顎涘┑鈽嗗灙閸嬫捇姊绘担鍛婂暈闁肩懓澧界划鏃堝箚閹靛啿寰?
     */
    private String getProductName(String productId) {
        if (productId == null || productId.trim().isEmpty()) return "";
        QueryWrapper<CloudPlan> qw = new QueryWrapper<>();
        qw.lambda().eq(CloudPlan::getPlanId, productId);
        CloudPlan plan = cloudPlanRepository.selectOne(qw);
        return plan != null ? plan.getName() : productId;
    }

    /**
     * 闂傚倷绀侀崥瀣磿閹惰棄搴婇柤鑹扮堪娴滃綊鏌涢妷銏℃珖缁炬儳銈搁悡顐﹀炊閵娧€濮囬梺杞扮劍閿曘垽寮诲☉銏″亜闂佸灝顑愬Λ鐐烘⒑鐠団€虫灍婵炲弶绮庨崚?
     */
    private String getDealerName(Long dealerId) {
        if (dealerId == null) return "";
        Dealer dealer = dealerRepository.selectById(dealerId);
        return dealer != null ? dealer.getName() : "";
    }

    /**
     * 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼幑鎰靛殭婵☆偅锕㈤弻娑㈠Ψ閿濆懎顬夐梺鍦厴娴滃爼寮诲☉妯滄梹鎷呴崷顓фК闂備礁鎲¤摫闁圭懓娲ら?
     */
    private void createTextCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    /**
     * 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼崜褏甯涢柣鎾冲€块弻鐔告綇閹呮В闂佸搫顦板浠嬪蓟濞戞鏃€鎷呴崷顓фК闂備礁鎲¤摫闁圭懓娲ら?
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
     * 闂傚倷绀侀崥瀣磿閹惰棄搴婇柤鑹扮堪娴滃綊鏌涢妷顔荤暗濞存粌缍婇弻鐔煎箚瑜嶉弳杈ㄣ亜閵堝懏鍤囬柡宀嬬秮閿濈偤顢楅埀顒佷繆娴犲鐓曢柍鍝勫€诲ú鎾煙椤栨艾鏆ｇ€规洜鍠栭、娆戝枈鏉堛剱鐔兼⒒娴ｅ憡鍟炴い銊ユ鐓ゆ慨妞诲亾闁诡垰娲╅妵鎰板箳閹寸姷鏆犻梻浣哄帶椤洟宕愬Δ鍛槬闁绘绮崑?
     * 闂備礁鎼ˇ顐﹀疾濠婂牆钃熼柕濞垮剭濞差亜鍐€妞ゆ劧绲介娑㈡⒑鐠団€崇€婚悘鐐村珟閿濆鈷戦悹鍥ｂ偓鍐差潾缂備緡鍣崹璺侯嚕閹惰姤鍋勯柣鎾冲閵夈儍褰掓晲閸噥浠╅梺姹囧劚閹虫ê顫忛悜妯诲劅闁规儳鍘栨竟鏇㈡⒒娴ｅ憡鎲搁柛鐘查铻炴俊銈呮噹閸ㄥ倿骞栫划瑙勵€嗘俊鎻掔墦閺屻劑寮崹顔规寖闂佸湱鍘ч崥瀣箞閵娾晜鍋￠柣妤€鐗嗛ˇ鈺呮⒑闁偛鑻晶鎵磼椤曞懎鐏︾€殿喗濞婇幃銏ゆ偂鎼达綆鍞?
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
        
        // 缂傚倸鍊搁崐鐑芥嚄閸洖绐楅柡鍥ュ焺閺佸洭鏌涘┑鍕姕濠殿垰銈搁弻鐔碱敍閸℃婀伴弽锟犳⒒娴ｇ瓔鍤冮柛鎾寸〒閸掓帡顢涢悙鍙夎緢?
        long totalDevices = 0;
        String installerCode = null;
        Long dealerId = null;
        
        // 闂備浇宕甸崰宥囩矆娓氣偓楠炲﹨绠涢幘顖涙暊闂侀€炲苯澧撮柡灞剧洴閹垽鏌ㄧ€ｅ灚锟ラ梻浣芥〃闂勫秹宕愬┑瀣祦?
        if (currentUser.getIsInstaller() != null && currentUser.getIsInstaller() == 1 && currentUser.getInstallerId() != null) {
            Installer installer = installerRepository.selectById(currentUser.getInstallerId());
            if (installer != null) {
                installerCode = installer.getInstallerCode();
                // 缂傚倸鍊搁崐鐑芥嚄閸洖绐楅柡鍥ュ焺閺佸洭鏌涘┑鍕姕闁哥喐鍨块弻娑樷槈閸楃偞鐏嶇紓浣靛妽瀹€鎼佸蓟濞戙垺鍋勯梺鍨儛濡稓绱撴担璇℃當闁硅櫕锕㈤獮鍡涘礃椤旇偐顦板銈嗘礀閹冲酣寮查鐔虹瘈闁靛骏绲剧涵鐐箾鐠囇呯暤鐎?
                QueryWrapper<ManufacturedDevice> deviceQw = new QueryWrapper<>();
                deviceQw.lambda().eq(ManufacturedDevice::getAssemblerCode, installerCode);
                totalDevices = deviceRepository.selectCount(deviceQw);
            }
        }
        
        // 缂傚倸鍊搁崐椋庣矆娴ｇ儤宕叉慨妞诲亾鐎殿喓鍔戞慨鈧柕鍫濇噽閺屽牓姊虹化鏇炲⒉闁荤噦绠撳瀹犵疀濞戞瑧鍘?
        if (currentUser.getIsDealer() != null && currentUser.getIsDealer() == 1 && currentUser.getDealerId() != null) {
            dealerId = currentUser.getDealerId();
            Dealer dealer = dealerRepository.selectById(dealerId);
            if (dealer != null) {
                // 缂傚倸鍊搁崐鐑芥嚄閸洖绐楅柡鍥ュ焺閺佸洭鏌涘┑鍕姢缁炬儳銈搁悡顐﹀炊閵娧€濮囬梺杞扮劍閿曘垽寮诲☉銏″亜闂佸灝顑愬Λ娑氱磽娴ｈ娈旈柟铏耿楠炲棝宕橀鑲╊槹濡炪倖娲栭幊搴ㄥ疾椤撶喓绡€闁靛骏绲剧涵鐐箾鐠囇呯暤鐎?
                QueryWrapper<ManufacturedDevice> deviceQw = new QueryWrapper<>();
                deviceQw.lambda().eq(ManufacturedDevice::getVendorCode, dealer.getDealerCode());
                long dealerDevices = deviceRepository.selectCount(deviceQw);
                // 婵犵數濮烽。浠嬪焵椤掆偓閸熷潡鍩€椤掆偓缂嶅﹪骞冨Ο璇茬窞闁归偊鍓涢惈鍕⒑闂堟稓绠氶柛鎾寸箘閻熝囨⒑閼姐倕鏋嶇紒鈧笟鈧獮濠呯疀閹绢垱鏁犻梺閫炲苯澧撮柡灞剧洴閹垽鏌ㄧ€ｅ灚顥ｉ柣搴㈩問閸犳岸寮拠宸殨濞村吋鎯岄弫宥嗙節婵犲倹鍣芥慨濠囩畺濮婄粯鎷呴崫銉︾殤闂佺顑嗛幑鍥蓟濞戙垺鍋勯梺鍨儜缁便劎绱撴担浠嬪摵缂佽鐗嗛悾鐑芥晲婢跺﹤鍞ㄥ銈嗗笒閸婂鎮伴埡鍌滅瘈闁靛繒濮烽崹濠氭煕閹板墎鍒板ù?
                totalDevices = Math.max(totalDevices, dealerDevices);
            }
        }
        
        // 缂傚倸鍊搁崐鐑芥嚄閸洖绐楅柡鍥ュ焺閺佸洭鏌涘┑鍕姢缁炬儳銈搁悡顐﹀炊閵娧€濮囬梺杞扮劍閿曘垽寮诲☉銏″亜闂佸灝顑愬Λ鐐烘⒑閹肩偛濡煎Δ鐘虫倐閸┿儲寰勯幇顓熸珕闁荤喐鐟辩徊鑺ョ閹€鏀介柣妯哄暱閸ゎ剟鏌?
        BigDecimal totalDealerAmount = BigDecimal.ZERO;
        if (dealerId != null) {
            List<String> dealerDeviceIds = resolveDealerDeviceIdsByDealerId(dealerId);
            QueryWrapper<PaymentOrder> orderQw = new QueryWrapper<>();
            orderQw.lambda().eq(PaymentOrder::getStatus, PaymentOrderStatus.PAID);
            final Long finalDealerId = dealerId;
            final List<String> finalDealerDeviceIds = dealerDeviceIds;
            if (!finalDealerDeviceIds.isEmpty()) {
                orderQw.and(wrapper -> wrapper
                        .eq("dealer_id", finalDealerId)
                        .or()
                        .in("device_id", finalDealerDeviceIds)
                );
            } else {
                orderQw.lambda().eq(PaymentOrder::getDealerId, dealerId);
            }
            if (startDate != null) {
                orderQw.lambda().ge(PaymentOrder::getPaidAt, startDate);
            }
            if (endDate != null) {
                orderQw.lambda().le(PaymentOrder::getPaidAt, endDate);
            }
            List<PaymentOrder> orders = orderRepository.selectList(orderQw);
            for (PaymentOrder order : orders) {
                DealerCommissionSlice slice = resolveDealerCommissionSlice(order, dealerId);
                if (slice != null) {
                    totalDealerAmount = totalDealerAmount.add(MoneyScaleUtil.keepTwoDecimals(slice.getAmount()));
                }
            }
        }
        
        result.put("totalDevices", totalDevices);
        result.put("totalDealerAmount", totalDealerAmount);
        
        log.info("闂傚倷绀侀崥瀣磿閹惰棄搴婇柤鑹扮堪娴滃綊鏌涢妷顔煎闁稿鍔戦弻鏇熺節韫囨洜鏆犻梺缁樻尰濞茬喖骞冭ぐ鎺戠疀闁告挷鑳堕弳鐘绘倵鐟欏嫭绀堥柛鐘插閺呫儵姊洪幖鐐插姌闁告柨顦靛畷? userId={}, totalDevices={}, totalDealerAmount={}", 
                currentUserId, totalDevices, totalDealerAmount);
        return result;
    }

    /**
     * 闂傚倷绀侀崥瀣磿閹惰棄搴婇柤鑹扮堪娴滃綊鏌涢妷銏℃珕闁哥喐鍨块弻娑樷槈閸楃偞鐏嶇紓浣靛妽瀹€鎼佸蓟濞戙垺鍋勯梺鍨儛濡稓绱撴担璇℃當闁硅櫕鍔欓獮蹇曗偓锝庡枛缁犳氨鎲稿鍏撅綁鎮介崨濠勫幗闂侀潧顭堥崕閬嶎敂椤忓牊鐓涘〒姘搐閺嬬喓绱掗崒娑樼瑨閾伙綁鏌ゅù瀣珔闁绘挻宀搁弻?
     * @param installerId 闂備浇宕甸崰宥囩矆娓氣偓楠炲﹨绠涢幘顖涙暊闂侀€炲苯澧撮柡灞剧洴閹垽鏌ㄧ€ｎ亞锛廌
     * @param startDate 闂佽瀛╅鏍窗閹烘纾婚柟鐐灱閺€鑺ャ亜閺冨倵鎷￠柛搴㈠姈娣囧﹪顢曢悢鍛婄彋閻?
     * @param endDate 缂傚倸鍊搁崐鐑芥倿閿曞倸绠伴悹鍥ф▕閻掕姤銇勯幇鍓佺暠缂侇偄绉归弻鏇熷緞濞戙垺顎嶉梺?
     */
    public Map<String, Object> getDevicePaymentStats(Long installerId, Date startDate, Date endDate) {
        // 闂傚倷绀侀崥瀣磿閹惰棄搴婇柤鑹扮堪娴滃綊鏌涢妷銏℃珕闁哥喐鍨块弻娑樷槈閸楃偞鐏嶇紓浣靛妽瀹€鎼佸蓟濞戙垺鍋勯梺鍨儛濡稓绱撴担璇℃當闁硅櫕锕㈤獮鍡涘礃椤旇偐顦板銈嗗坊閸嬫挻銇勯敐鍡欏弨闁哄矉绻濆畷鐓庘攽閹邦厜顏勵渻閵堝棙鈷愭繛鍙壝?
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
     * 闂傚倷绀佺紞濠傤焽瑜忕槐鐐寸節閸パ囨７闂佹儳绻愬﹢杈╁婵傚憡鐓欓柟顖嗗喚鏆㈤梺鍝勬－閸撶喖骞冪憴鍕闂傚牊绋撴禒濂告倵?
     * @param orderIds 闂備浇宕垫慨鎶芥⒔瀹ュ鍨傞柣鐔稿閺嗭箓鏌ｉ幋鐘虫嚈闂傚倷绀侀幉锛勬暜濡ゅ懌鈧啯寰勯幇顑?
     * @param operatorId 闂傚倷鑳堕幊鎾绘倶濠靛牏鐭撶€规洖娲ㄧ粈濠囨煛閸愩劎澧曠€瑰憡绻冩穱濠囧Χ閸ャ劌鐝?
     * @return 缂傚倸鍊搁崐鐑芥倿閿曞倸绠板Δ锝呭暞閸嬧晛鈹戦悩瀹犲缂佺媴缍侀弻鐔兼焽閿曗偓婢ь喗銇勯銈呪枅闁哄矉缍佹俊鎼佸Ψ閵夘喕绱撴俊鐐€栭幐鍝ョ礊婵犲倻鏆﹂柨鐔哄Т閸楁娊鏌ｉ弬鎸庡暈妞ゆ柨绉瑰?
     */
    @Transactional
    public int settleOrders(List<String> orderIds, Long operatorId, String dimension) {
        if (orderIds == null || orderIds.isEmpty()) {
            return 0;
        }
        String normalizedDimension = normalizeDimension(dimension);
        if (normalizedDimension == null) {
            throw new IllegalArgumentException("Invalid settlement dimension, only installer/dealer supported");
        }

        log.info("Settle orders in batch: operatorId={}, dimension={}, orderIds={}", operatorId, normalizedDimension, orderIds);

        int count = 0;
        Date now = new Date();
        for (String orderId : orderIds) {
            QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();
            qw.lambda().eq(PaymentOrder::getOrderId, orderId)
                    .eq(PaymentOrder::getStatus, PaymentOrderStatus.PAID);
            PaymentOrder order = orderRepository.selectOne(qw);
            if (order == null) {
                continue;
            }
            if (isSettledByDimension(order, normalizedDimension)) {
                continue;
            }

            markOrderSettledByDimension(order, normalizedDimension);
            order.setUpdatedAt(now);
            orderRepository.updateById(order);
            count++;
        }
        log.info("Batch settlement finished: operatorId={}, dimension={}, settledCount={}", operatorId, normalizedDimension, count);
        return count;
    }

    /**
     * 闂傚倷绀侀崥瀣磿閹惰棄搴婇柤鑹扮堪娴滃綊鏌涢妷銏℃珖缁炬儳銈搁弻鐔煎箚瑜嶉。鎶芥煛閸♀晛澧撮柟顔款潐閹峰懘妫冨☉姘偅闁诲氦顫夊ú婊堝窗閺嵮呮殾婵﹩鍎甸弮鍫濈闁宠鍎虫禍?
     * @param installerCode 闂備浇宕甸崰宥囩矆娓氣偓楠炲﹨绠涢幘顖涙暊闂侀€炲苯澧撮柡灞剧洴閹垽鏌ㄧ€ｅ灚顥犻梻浣告憸閸庢劙宕滈悢鐓庣畾鐎广儱妫涢悷鐟扳攽閻樻彃顏い锔规櫊濮婃椽宕ㄦ繝鍕櫧闂佹悶鍔岄悥濂稿春閳ь剚銇勯幒鍡椾壕濡炪伇鈧崑鎾剁磽?
     * @param dealerId 缂傚倸鍊搁崐椋庣矆娴ｇ儤宕叉慨妞诲亾鐎殿喓鍔戞慨鈧柕鍫濇噽閺屽牓姊洪崨濠勭煂闁伙附宀稿娲箰鎼达絺妲堥梺缁橆殔濡粓濡甸幇鏉胯摕闁靛濡囬崝鍨節閵忥絽鐓愮紒瀣尵缁?
     * @param isSettled 缂傚倸鍊搁崐鐑芥倿閿曞倸绠板Δ锝呭暞閸嬧晛鈹戦悩宕囶暡闁稿鍔欓弻銈夊传閵夘喗姣岄梺绋款儐閹歌崵绮悢鍝ョ瘈闁告洦鍘惧畷绉恥ll-闂傚倷鑳堕…鍫㈡崲閸儱绀夐柟杈剧畱绾惧潡鏌熺紒銏犳灍闁?-闂傚倷绀侀幖顐︽偋濠婂嫮顩查柣鎰版涧閸ㄦ繈鏌曟繛褍鎳嶇粭澶娾攽鎺抽崐鏇㈩敄閸モ晝鐭?-闂佽姘﹂～澶愭偤閺囩姳鐒婃繛鍡樺灥閸ㄦ繈鏌曟繛褍鎳嶇粭?
     * @param startDate 闂佽瀛╅鏍窗閹烘纾婚柟鐐灱閺€鑺ャ亜閺冨倵鎷￠柛搴㈠姈娣囧﹪顢曢悢鍛婄彋閻?
     * @param endDate 缂傚倸鍊搁崐鐑芥倿閿曞倸绠伴悹鍥ф▕閻掕姤銇勯幇鍓佺暠缂侇偄绉归弻鏇熷緞濞戙垺顎嶉梺?
     * @param page 婵犵绱曢崑鎴﹀磹濡ゅ懎鏋侀柟闂寸劍閸?
     * @param size 濠电姵顔栭崳顖滃緤閻ｅ本宕叉慨妞诲亾濠碘€崇摠瀵板嫰骞囬鍌炵崜闂備焦鏋奸弲娑㈠疮椤栨埃鏋?
     */
    public Map<String, Object> getSettlementOrders(String installerCode, Long dealerId, Integer isSettled,
                                                   String dimension, Date startDate, Date endDate,
                                                   Integer page, Integer size) {
        List<String> dealerDeviceIds = dealerId != null
                ? resolveDealerDeviceIdsByDealerId(dealerId)
                : Collections.emptyList();
        String normalizedDimension = resolveSettlementDimension(dimension, installerCode, dealerId);

        QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();
        qw.lambda().eq(PaymentOrder::getStatus, PaymentOrderStatus.PAID);
        
        if (installerCode != null && !installerCode.trim().isEmpty()) {
            qw.lambda().eq(PaymentOrder::getInstallerCode, installerCode);
        }
        if (dealerId != null) {
            final Long finalDealerId = dealerId;
            final List<String> finalDealerDeviceIds = dealerDeviceIds;
            if (!finalDealerDeviceIds.isEmpty()) {
                qw.and(wrapper -> wrapper
                        .eq("dealer_id", finalDealerId)
                        .or()
                        .in("device_id", finalDealerDeviceIds)
                );
            } else {
                qw.lambda().eq(PaymentOrder::getDealerId, dealerId);
            }
        }
        if (isSettled != null) {
            if (DIMENSION_DEALER.equals(normalizedDimension)) {
                qw.apply("COALESCE(dealer_is_settled, is_settled, 0) = {0}", isSettled);
            } else {
                qw.apply("COALESCE(installer_is_settled, is_settled, 0) = {0}", isSettled);
            }
        }
        if (startDate != null) {
            qw.lambda().ge(PaymentOrder::getPaidAt, startDate);
        }
        if (endDate != null) {
            qw.lambda().le(PaymentOrder::getPaidAt, endDate);
        }
        qw.lambda().orderByDesc(PaymentOrder::getPaidAt);

        // 闂傚倷绀侀幉锛勬暜閹烘嚦娑樷攽鐎ｎ€儱顭块懜闈涘闁藉啰鍠栭弻鏇熷緞濡厧甯ラ梺?
        int offset = (page - 1) * size;
        QueryWrapper<PaymentOrder> countQw = qw.clone();
        qw.last("LIMIT " + offset + ", " + size);
        List<PaymentOrder> list = orderRepository.selectList(qw);
        if (dealerId != null) {
            applyDealerCommissionSlice(list, dealerId);
        }
        for (PaymentOrder order : list) {
            order.setIsSettled(getSettledValueByDimension(order, normalizedDimension));
        }
        long total = orderRepository.selectCount(countQw);

        // 闂備浇宕垫慨宕囨閵堝洦顫曢柡鍥ュ灪閸嬧晠鎮归崶銊ф殭鐟滄柨鐣烽崼鏇炍╅柕蹇曞С婢规洜绱撻崒娆戝妽閽冭鲸绻涢崼婵堝煟闁?
        QueryWrapper<PaymentOrder> sumQw = new QueryWrapper<>();
        sumQw.lambda().eq(PaymentOrder::getStatus, PaymentOrderStatus.PAID);
        if (installerCode != null && !installerCode.trim().isEmpty()) {
            sumQw.lambda().eq(PaymentOrder::getInstallerCode, installerCode);
        }
        if (dealerId != null) {
            final Long finalDealerId = dealerId;
            final List<String> finalDealerDeviceIds = dealerDeviceIds;
            if (!finalDealerDeviceIds.isEmpty()) {
                sumQw.and(wrapper -> wrapper
                        .eq("dealer_id", finalDealerId)
                        .or()
                        .in("device_id", finalDealerDeviceIds)
                );
            } else {
                sumQw.lambda().eq(PaymentOrder::getDealerId, dealerId);
            }
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
            BigDecimal amount = MoneyScaleUtil.keepTwoDecimals(order.getAmount());
            // 闂傚倷绀侀幖顐ょ矓閻戞枻缍栧璺猴功閺嗐倕銆掑锝呬壕閻庢鍠栭…閿嬩繆閻戣В鈧箓骞嬪┑鍥╂殸缂傚倸鍊风欢锟犲磻婢舵劦鏁嬬憸鏃堝箖濡や緡妲归幖娣灩閸樼懓顪冮妶鍡橆梿闁稿鍔欏铏鐎涙鍘遍梺鍦劋閸ㄥ爼藟閻愬樊娼＄憸宥夋煀閿濆钃熺€光偓閸曨偆顓洪梺鎸庢⒒缁垰顭块幘缁樷拺闁圭娴烽埥澶愭煟濡や礁濮嶉挊鐔封攽閻樺弶鎼愰悗姘樀閺屾稑鈹戦崱妤婁痪濠?缂傚倸鍊搁崐椋庣矆娴ｇ儤宕叉慨妞诲亾鐎殿喓鍔戞慨鈧柕鍫濇噽閺屽牓姊虹化鏇燁潑闁告ê澧界划?
            BigDecimal profit;
            if (DIMENSION_DEALER.equals(normalizedDimension)) {
                if (dealerId != null) {
                    DealerCommissionSlice slice = resolveDealerCommissionSlice(order, dealerId);
                    profit = slice != null ? MoneyScaleUtil.keepTwoDecimals(slice.getAmount()) : BigDecimal.ZERO;
                } else {
                    profit = order.getDealerAmount() != null ? MoneyScaleUtil.keepTwoDecimals(order.getDealerAmount()) : BigDecimal.ZERO;
                }
            } else {
                profit = order.getInstallerAmount() != null ? MoneyScaleUtil.keepTwoDecimals(order.getInstallerAmount()) : BigDecimal.ZERO;
            }
            
            totalAmount = totalAmount.add(amount);
            totalProfitAmount = totalProfitAmount.add(profit);
            
            if (isSettledByDimension(order, normalizedDimension)) {
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

    // 缂傚倸鍊搁崐鐑芥倿閿曞倸绠板Δ锝呭暞閸嬧晠鏌ゆ慨鎰偓鏍磻濮椻偓閺屽秹濡烽绛嬩淮cel闂傚倷绀侀幉锛勬暜濡ゅ懌鈧啴宕卞☉娆忎函闂佹寧绻傚Λ瀵告?
    private static final String[] SETTLEMENT_HEADERS = {
            "订单号", "设备ID", "上线国家", "装机商代码", "装机商名称", "经销商ID", "经销商名称",
            "套餐名称", "支付金额", "支付币种", "支付时间", "分润金额", "结算状态"
    };

    /**
     * 闂備浇顕уù鐑藉极閹间礁鍌ㄧ憸鏂跨暦閻㈠壊鏁囬柣妯兼暩閿涙粓姊虹憴鍕姢濠⒀冮叄瀵娊顢楅崟顒€浠╁┑鐐村灦瀹稿宕戦幘瀹︺劎绱炵欢鈧琹
     * 闂傚倷绀侀幖顐λ囬鐐茬柈闁哄鍩堥悗鍫曟煣韫囨凹娼愰悗姘哺閹鈽夊▍铏灴閿濈偤寮介鐔哄弳濠电偞鍨堕悷銉╁传閻戞绠剧痪鏉垮綁缁ㄧ晫绱掗纰辩吋闁轰礁绉瑰畷鐔碱敆閳ь剟鍩€椤掑倸浠遍柡灞剧☉閳藉鈻嶉褌娴烽柕鍥ㄥ姌椤﹀綊鏌熼搹顐疁闁圭锕ュ鍕熼悜姗嗗晫闂傚倷绀侀幉锟犲垂閻撳寒娴栭柕濞у懐鐣堕悗鍏夊亾闁告洦鍓欏▓婊冾渻閵堝懐绠伴悗姘煎枤缁骞掑Δ浣哄幈闂婎偄娲﹂幐楣冨几閸愵煁褰掓偐閼碱剙鈪甸梺璇″灠閸熸潙鐣烽悢纰辨晢濞达綀顕栭崯鈧梻?
     */
    public Map<String, Object> exportSettlementExcel(Long currentUserId, String installerCode, Long dealerId, 
                                                      Date startDate, Date endDate) throws IOException {
        // 闂傚倷绀侀幖顐ょ矓閻戞枻缍栧璺猴功閺嗐倕霉閿濆拋娼熷ù婊冪秺閺岀喖骞嗚閺嗚鲸銇勯妶鍛殗闁哄矉缍侀敐鐐侯敆閳ь剚淇婃禒瀣厱闁冲搫鍊婚妴鎺楁煙閸欏灏︽鐐村浮瀵挳鎮滈崱姗嗘缂傚倷鑳堕搹搴ㄥ矗鎼淬劌绐楅柡宥庡幗閸嬧晠鏌ｉ幇闈涘濞存嚎鍊濋弻娑㈠Ψ閿濆懎顬夋繝娈垮枔閸ㄤ粙寮婚埄鍐ㄧ窞闁糕€崇箰娴滈箖鏌涘▎蹇ｆ▓闁?
        String effectiveInstallerCode = installerCode;
        Long effectiveDealerId = dealerId;
        
        if (currentUserId != null) {
            User currentUser = userRepository.selectById(currentUserId);
            if (currentUser != null) {
                boolean isAdmin = currentUser.getRole() != null && currentUser.getRole() == 3;
                if (!isAdmin) {
                    // 闂備浇宕甸崰宥囩矆娓氣偓楠炲﹨绠涢幘顖涙暊闂侀€炲苯澧撮柡灞剧洴閹垽鏌ㄧ€ｅ灚顥ｉ柣鐔哥矋婵℃悂宕堕妸銉︾暠闂備浇濮ら敋妞わ缚绮欏畷鐢稿Χ婢跺鍘遍梺纭呮彧婵″洨妲愭导瀛樼厱闁靛牆妫涢惌濠冦亜閺傝法绠荤€规洩绻濋幃娆撳箵閹烘梻鈽夐梻鍌欐祰婢瑰牊銇旂粙娆炬綎閻犲洦绁撮弸鏃堟煙鏉堝墽鐣遍柡瀣╃窔閺岋絽螖閳ь剟鎮ч崱娆戠焾?
                    if (currentUser.getIsInstaller() != null && currentUser.getIsInstaller() == 1 && currentUser.getInstallerId() != null) {
                        Installer installer = installerRepository.selectById(currentUser.getInstallerId());
                        if (installer != null) {
                            effectiveInstallerCode = installer.getInstallerCode();
                        }
                    }
                    // 缂傚倸鍊搁崐椋庣矆娴ｇ儤宕叉慨妞诲亾鐎殿喓鍔戞慨鈧柕鍫濇噽閺屽牓姊虹化鏇炲⒉妞ゃ劌鎳庨埥澶庮樄闁哄被鍊楅埀顒€婀辨慨鐢杆夋径瀣ㄤ簻闁哄浂婢€閹插墽鈧娲橀悷锕傚Χ閿曞倸鍨傛い鏃傜摂閸炲爼鏌ｆ惔鈩冭础妞ゎ厼鐗嗛悾鐑筋敆閸曨偆鐓戦悷婊勬楠炲﹪寮撮姀鐘电潉闂佺鏈粙鎾诲汲閻樼粯鈷戦弶鐐村閸忓苯顭胯椤ㄥ懘鎮?
                    if (currentUser.getIsDealer() != null && currentUser.getIsDealer() == 1 && currentUser.getDealerId() != null) {
                        effectiveDealerId = currentUser.getDealerId();
                    }
                }
            }
        }
        
        log.info("闂備浇顕уù鐑藉极閹间礁鍌ㄧ憸鏂跨暦閻㈠壊鏁囬柣妯兼暩閿涙粓姊虹憴鍕姢濠⒀冮叄瀵娊顢楅崟顒€浠? currentUserId={}, installerCode={}, dealerId={}, startDate={}, endDate={}", 
                currentUserId, effectiveInstallerCode, effectiveDealerId, startDate, endDate);

        List<String> dealerDeviceIds = effectiveDealerId != null
                ? resolveDealerDeviceIdsByDealerId(effectiveDealerId)
                : Collections.emptyList();

        QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();
        qw.lambda().eq(PaymentOrder::getStatus, PaymentOrderStatus.PAID);
        if (effectiveInstallerCode != null && !effectiveInstallerCode.trim().isEmpty()) {
            qw.lambda().eq(PaymentOrder::getInstallerCode, effectiveInstallerCode);
        }
        if (effectiveDealerId != null) {
            final Long finalDealerId = effectiveDealerId;
            final List<String> finalDealerDeviceIds = dealerDeviceIds;
            if (!finalDealerDeviceIds.isEmpty()) {
                qw.and(wrapper -> wrapper
                        .eq("dealer_id", finalDealerId)
                        .or()
                        .in("device_id", finalDealerDeviceIds)
                );
            } else {
                qw.lambda().eq(PaymentOrder::getDealerId, effectiveDealerId);
            }
        }
        if (startDate != null) {
            qw.lambda().ge(PaymentOrder::getPaidAt, startDate);
        }
        if (endDate != null) {
            qw.lambda().le(PaymentOrder::getPaidAt, endDate);
        }
        qw.lambda().orderByDesc(PaymentOrder::getPaidAt);

        List<PaymentOrder> orders = orderRepository.selectList(qw);
        if (effectiveDealerId != null) {
            applyDealerCommissionSlice(orders, effectiveDealerId);
        }
        boolean isDealerSettlement = effectiveDealerId != null;
        
        byte[] excelData = createSettlementExcel(orders, isDealerSettlement);
        
        Map<String, Object> result = new HashMap<>();
        result.put("isZip", false);
        result.put("data", excelData);
        return result;
    }

    /**
     * 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼幆褍顣崇痪鎯с偢閺岀喖骞嗚椤ｆ娊鏌￠崱鈺佸⒋闁诡喛娉涢埥澶愬箳閸℃ぞ澹曞銇卞懏鐦╡l
     */
    private byte[] createSettlementExcel(List<PaymentOrder> orders, boolean isDealerSettlement) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("结算表");

            // 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼幑鎰靛殭闁告劏鍋撻梻浣规偠閸庢椽宕滈敃鍌氭辈妞ゆ牜鍋為悡娑氣偓骞垮劚閹冲骸危閼姐倗纾?
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

            // 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼幑鎰靛殭闁哄绶氶弻锝呂旈埀顒勬偋閸℃瑧鐭堥柨鏇炲€归悡娑氣偓骞垮劚閹冲骸危閼姐倗纾?
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            // 闂傚倷绀侀幉锛勬暜濡ゅ啰鐭欓柟瀵稿Х绾句粙鏌熼崜褏甯涢柣鎾冲€块弻鐔告綇閹呮В闂佸搫顦板浠嬪蓟閳ユ剚鍚嬮柛娑卞幗浜涚紓?
            CellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.setBorderBottom(BorderStyle.THIN);
            moneyStyle.setBorderTop(BorderStyle.THIN);
            moneyStyle.setBorderLeft(BorderStyle.THIN);
            moneyStyle.setBorderRight(BorderStyle.THIN);
            DataFormat format = workbook.createDataFormat();
            moneyStyle.setDataFormat(format.getFormat("#,##0.00"));

            // 闂傚倷绀侀幉锟犲礉閺嶎厽鍋￠柕澶嗘櫅閻鏌涢埄鍐槈闁告劏鍋撻梻浣规偠閸庢椽宕滈敃鍌氭辈妞ゆ牜鍋為崑?
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < SETTLEMENT_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(SETTLEMENT_HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // 闂傚倷绀侀幉锟犲礉閺嶎厽鍋￠柕澶嗘櫅閻鏌涢埄鍐槈闁哄绶氶弻锝呂旈埀顒勬偋閸℃瑧鐭堥柨鏇炲€归崑?
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
                
                // 闂傚倷绀侀幉锛勬暜閹烘嚦娑樷枎閹板洦顨嗛幆鏃堝Ω閿旀儳甯撻梻浣侯攰閹活亞鈧潧鐭傚璺好洪鍛弳濠电偞鍨堕…鍥ㄦ櫠濞戙垺鐓熸俊銈勭劍鐏忥附顨ラ悙鏉戠伌妞ゃ垺鐟╅獮鎴﹀箛閸偉鏅ч梻鍌欑劍閹爼宕濆畝鍕垫晞闁瑰濮甸～?
                BigDecimal profit = isDealerSettlement ? 
                        (order.getDealerAmount() != null ? order.getDealerAmount() : BigDecimal.ZERO) :
                        (order.getInstallerAmount() != null ? order.getInstallerAmount() : BigDecimal.ZERO);
                createMoneyCell(row, col++, profit, moneyStyle);
                totalProfit = totalProfit.add(profit);
                
                boolean settled = isDealerSettlement ? isDealerSettled(order) : isInstallerSettled(order);
                createTextCell(row, col++, settled ? "已结算" : "未结算", dataStyle);
            }

            // 闂傚倷绀侀幉锟犲礉閺嶎厽鍋￠柕澶嗘櫅閻鏌涢埄鍐︿簼鐟滄柨鐣烽崼鏇炍╅柕蹇曞С婢规洟姊洪崫鍕仼婵炲瓨宀稿?
            Row sumRow = sheet.createRow(rowNum);
            Cell sumLabelCell = sumRow.createCell(0);
            sumLabelCell.setCellValue("合计");
            sumLabelCell.setCellStyle(headerStyle);
            
            Cell sumProfitCell = sumRow.createCell(11);
            sumProfitCell.setCellValue(totalProfit.doubleValue());
            sumProfitCell.setCellStyle(moneyStyle);

            // 闂傚倷鑳堕崢褔銆冩惔銏㈩洸婵犲﹤瀚崣蹇涙煃閸濆嫬鏆為悗姘煎墴閺屾盯骞囬妸锔界彆濠电偛鐗嗛…鐑藉蓟濞戙垹绠抽柟鍨暞閻ｈ泛顪?
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

