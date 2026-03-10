package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.domain.*;
import com.pura365.camera.enums.CommissionFeeType;
import com.pura365.camera.enums.CommissionProfitMode;
import com.pura365.camera.enums.PaymentOrderStatus;
import com.pura365.camera.model.report.PageResult;
import com.pura365.camera.model.report.RechargeOrderQueryRequest;
import com.pura365.camera.model.report.RechargeOrderReportVO;
import com.pura365.camera.repository.*;
import com.pura365.camera.util.MoneyScaleUtil;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 充值订单报表服务
 * 提供订单查询和Excel导出功能
 * 
 * 核心逻辑：订单表快照了经销商/业务员信息，直接使用订单表的字段查询
 * 设备表 ManufacturedDevice 作为补充（用于获取装机商代码等订单未快照的字段）
 */
@Service
public class RechargeOrderReportService {

    private static final Logger log = LoggerFactory.getLogger(RechargeOrderReportService.class);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Autowired
    private PaymentOrderRepository orderRepository;

    @Autowired
    private CloudPlanRepository planRepository;

    @Autowired
    private PlanCommissionRepository commissionRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private ManufacturedDeviceRepository deviceRepository;

    @Autowired
    private SalesmanRepository salesmanRepository;

    @Autowired
    private PlanCommissionService commissionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InstallerRepository installerRepository;

    @Autowired
    private DealerRepository dealerRepository;

    @Autowired
    private DeviceDealerRepository deviceDealerRepository;

    // 报表Excel列标题
    private static final String[] EXCEL_HEADERS = {
            "充值订单号", "下单时间", "设备ID", "我的身份", "所属经销商",
            "套餐ID", "套餐名称", "套餐类型", "云存类型", "售价",
            "支付金额", "支付币种", "收款主体", "手续费率", "手续费",
            "套餐返点", "套餐成本", "可分润金额", "分润模式",
            "装机商分润比例", "装机商分润", "一级分润比例", "一级分润",
            "二级分润比例", "二级分润", "支付通道", "支付订单号",
            "支付时间", "三方订单号"
    };

    /**
     * 分页查询充值订单报表
     * 
     * 查询逻辑：直接使用订单表的快照字段进行筛选
     */
    public PageResult<RechargeOrderReportVO> queryOrders(RechargeOrderQueryRequest request) {
        log.info("查询充值订单报表: page={}, size={}, vendorCode={}, salesmanId={}",
                request.getPage(), request.getSize(), request.getVendorCode(), request.getSalesmanId());

        DealerQueryScope queryScope = resolveDealerQueryScope(request);

        // 直接使用订单表的快照字段查询
        QueryWrapper<PaymentOrder> qw = buildQueryWrapper(request, queryScope);

        // 分页查询
        int offset = (request.getPage() - 1) * request.getSize();
        qw.last("LIMIT " + offset + ", " + request.getSize());
        List<PaymentOrder> orders = orderRepository.selectList(qw);

        // 查询总数
        QueryWrapper<PaymentOrder> countQw = buildQueryWrapper(request, queryScope);
        long total = orderRepository.selectCount(countQw);

        // 转换为报表VO
        List<RechargeOrderReportVO> voList = new ArrayList<>();
        for (PaymentOrder order : orders) {
            voList.add(convertToReportVO(order, queryScope));
        }

        log.info("查询充值订单报表完成: total={}, listSize={}", total, voList.size());
        return PageResult.of(voList, total, request.getPage(), request.getSize());
    }

    /**
     * 查询所有符合条件的订单（用于导出，不分页）
     */
    public List<RechargeOrderReportVO> queryAllOrders(RechargeOrderQueryRequest request) {
        log.info("查询充值订单用于导出");

        DealerQueryScope queryScope = resolveDealerQueryScope(request);

        // 直接使用订单表的快照字段查询
        QueryWrapper<PaymentOrder> qw = buildQueryWrapper(request, queryScope);
        qw.lambda().orderByDesc(PaymentOrder::getCreatedAt);

        List<PaymentOrder> orders = orderRepository.selectList(qw);

        List<RechargeOrderReportVO> voList = new ArrayList<>();
        for (PaymentOrder order : orders) {
            voList.add(convertToReportVO(order, queryScope));
        }

        log.info("查询充值订单用于导出完成: count={}", voList.size());
        return voList;
    }

    /**
     * 导出充值订单报表为Excel
     */
    public byte[] exportToExcel(RechargeOrderQueryRequest request) throws IOException {
        log.info("开始导出充值订单报表Excel");

        List<RechargeOrderReportVO> orders = queryAllOrders(request);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("充值订单报表");

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
            for (int i = 0; i < EXCEL_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(EXCEL_HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // 写入数据行
            int rowNum = 1;
            for (RechargeOrderReportVO vo : orders) {
                Row row = sheet.createRow(rowNum++);
                int col = 0;

                // 订单基本信息
                createCell(row, col++, vo.getOrderId(), dataStyle);
                createCell(row, col++, vo.getCreatedAt() != null ? sdf.format(vo.getCreatedAt()) : "", dataStyle);
                createCell(row, col++, vo.getDeviceId(), dataStyle);
                createCell(row, col++, vo.getUserRole(), dataStyle);
                createCell(row, col++, vo.getVendorName(), dataStyle);

                // 套餐信息
                createCell(row, col++, vo.getPlanId(), dataStyle);
                createCell(row, col++, vo.getPlanName(), dataStyle);
                createCell(row, col++, vo.getPlanTypeName(), dataStyle);
                createCell(row, col++, vo.getCloudType(), dataStyle);
                createMoneyCell(row, col++, vo.getOriginalPrice(), moneyStyle);

                // 支付信息
                createMoneyCell(row, col++, vo.getPayAmount(), moneyStyle);
                createCell(row, col++, vo.getCurrencyName(), dataStyle);
                createCell(row, col++, vo.getPayeeEntity(), dataStyle);
                createCell(row, col++, vo.getFeeRateDesc(), dataStyle);
                createMoneyCell(row, col++, vo.getFeeAmount(), moneyStyle);

                // 财务信息
                createCell(row, col++, vo.getRebateDesc(), dataStyle);
                createMoneyCell(row, col++, vo.getPlanCost(), moneyStyle);
                createMoneyCell(row, col++, vo.getProfitAmount(), moneyStyle);
                createCell(row, col++, vo.getProfitModeName(), dataStyle);

                // 分润信息
                createCell(row, col++, vo.getInstallerRateDesc(), dataStyle);
                createMoneyCell(row, col++, vo.getInstallerAmount(), moneyStyle);
                createCell(row, col++, vo.getLevel1RateDesc(), dataStyle);
                createMoneyCell(row, col++, vo.getLevel1Amount(), moneyStyle);
                createCell(row, col++, vo.getLevel2RateDesc(), dataStyle);
                createMoneyCell(row, col++, vo.getLevel2Amount(), moneyStyle);

                // 支付详情
                createCell(row, col++, vo.getPaymentMethodName(), dataStyle);
                createCell(row, col++, vo.getPayOrderId(), dataStyle);
                createCell(row, col++, vo.getPaidAt() != null ? sdf.format(vo.getPaidAt()) : "", dataStyle);
                createCell(row, col++, vo.getThirdOrderId(), dataStyle);
            }

            // 自动调整列宽
            for (int i = 0; i < EXCEL_HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
                // 设置最小宽度
                int width = sheet.getColumnWidth(i);
                if (width < 3000) {
                    sheet.setColumnWidth(i, 3000);
                } else if (width > 15000) {
                    sheet.setColumnWidth(i, 15000);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);

            log.info("导出充值订单报表Excel完成: rowCount={}", orders.size());
            return out.toByteArray();
        }
    }

    private DealerQueryScope resolveDealerQueryScope(RechargeOrderQueryRequest request) {
        String effectiveInstallerCode = request.getInstallerCode();
        String effectiveDealerCode = request.getVendorCode();
        Long effectiveDealerId = null;
        boolean needOrCondition = false;

        if (request.getCurrentUserId() != null) {
            User currentUser = userRepository.selectById(request.getCurrentUserId());
            if (currentUser != null) {
                boolean isAdmin = currentUser.getRole() != null && currentUser.getRole() == 3;

                if (!isAdmin) {
                    boolean isInstaller = currentUser.getIsInstaller() != null
                            && currentUser.getIsInstaller() == 1
                            && currentUser.getInstallerId() != null;
                    boolean isDealer = currentUser.getIsDealer() != null
                            && currentUser.getIsDealer() == 1
                            && currentUser.getDealerId() != null;

                    if (isInstaller) {
                        Installer installer = installerRepository.selectById(currentUser.getInstallerId());
                        if (installer != null && installer.getInstallerCode() != null) {
                            effectiveInstallerCode = installer.getInstallerCode();
                            log.info("装机商用户查询充值订单, userId={}, installerCode={}", request.getCurrentUserId(), effectiveInstallerCode);
                        }
                    }

                    if (isDealer) {
                        Dealer dealer = dealerRepository.selectById(currentUser.getDealerId());
                        if (dealer != null && dealer.getDealerCode() != null) {
                            effectiveDealerCode = dealer.getDealerCode();
                            effectiveDealerId = dealer.getId();
                            log.info("经销商用户查询充值订单, userId={}, dealerCode={}", request.getCurrentUserId(), effectiveDealerCode);
                        } else {
                            effectiveDealerId = currentUser.getDealerId();
                        }
                    }

                    needOrCondition = isInstaller && isDealer;
                }
            }
        }

        List<String> dealerDeviceIds = resolveDealerDeviceIds(effectiveDealerCode);
        return new DealerQueryScope(effectiveInstallerCode, effectiveDealerCode, effectiveDealerId, dealerDeviceIds, needOrCondition);
    }

    private QueryWrapper<PaymentOrder> buildQueryWrapper(RechargeOrderQueryRequest request, DealerQueryScope queryScope) {
        QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();

        String effectiveInstallerCode = queryScope != null ? queryScope.getInstallerCode() : request.getInstallerCode();
        String effectiveDealerCode = queryScope != null ? queryScope.getDealerCode() : request.getVendorCode();
        boolean needOrCondition = queryScope != null && queryScope.isNeedOrCondition();
        List<String> dealerDeviceIds = queryScope != null
                ? queryScope.getDealerDeviceIds()
                : resolveDealerDeviceIds(effectiveDealerCode);

        if (request.getOrderId() != null && !request.getOrderId().trim().isEmpty()) {
            qw.lambda().like(PaymentOrder::getOrderId, request.getOrderId());
        }
        if (request.getDeviceId() != null && !request.getDeviceId().trim().isEmpty()) {
            qw.lambda().like(PaymentOrder::getDeviceId, request.getDeviceId());
        }

        // 如果既是装机商又是经销商，使用OR条件
        if (needOrCondition
                && effectiveInstallerCode != null && !effectiveInstallerCode.trim().isEmpty()
                && effectiveDealerCode != null && !effectiveDealerCode.trim().isEmpty()) {
            final String finalInstallerCode = effectiveInstallerCode;
            final String finalDealerCode = effectiveDealerCode;
            final List<String> finalDealerDeviceIds = dealerDeviceIds;
            if (!finalDealerDeviceIds.isEmpty()) {
                qw.and(wrapper -> wrapper
                        .eq("installer_code", finalInstallerCode)
                        .or()
                        .eq("dealer_code", finalDealerCode)
                        .or()
                        .in("device_id", finalDealerDeviceIds)
                );
            } else {
                qw.and(wrapper -> wrapper
                        .eq("installer_code", finalInstallerCode)
                        .or()
                        .eq("dealer_code", finalDealerCode)
                );
            }
        } else {
            // 经销商过滤
            if (effectiveDealerCode != null && !effectiveDealerCode.trim().isEmpty()) {
                final String finalDealerCode = effectiveDealerCode;
                final List<String> finalDealerDeviceIds = dealerDeviceIds;
                if (!finalDealerDeviceIds.isEmpty()) {
                    qw.and(wrapper -> wrapper
                            .eq("dealer_code", finalDealerCode)
                            .or()
                            .in("device_id", finalDealerDeviceIds)
                    );
                } else {
                    qw.lambda().eq(PaymentOrder::getDealerCode, effectiveDealerCode);
                }
            }
            // 装机商过滤
            if (effectiveInstallerCode != null && !effectiveInstallerCode.trim().isEmpty()) {
                qw.lambda().eq(PaymentOrder::getInstallerCode, effectiveInstallerCode);
            }
        }
        // salesmanId 字段已废弃，PaymentOrder 表中不存在此字段
        // if (request.getSalesmanId() != null) {
        //     qw.lambda().eq(PaymentOrder::getSalesmanId, request.getSalesmanId());
        // }
        
        if (request.getPlanId() != null && !request.getPlanId().trim().isEmpty()) {
            qw.lambda().eq(PaymentOrder::getProductId, request.getPlanId());
        }
        if (request.getPaymentMethod() != null && !request.getPaymentMethod().trim().isEmpty()) {
            qw.lambda().eq(PaymentOrder::getPaymentMethod, request.getPaymentMethod());
        }
        if (request.getCurrency() != null && !request.getCurrency().trim().isEmpty()) {
            qw.lambda().eq(PaymentOrder::getCurrency, request.getCurrency());
        }
        if (request.getStatus() != null && !request.getStatus().trim().isEmpty()) {
            PaymentOrderStatus statusEnum = PaymentOrderStatus.fromCode(request.getStatus());
            if (statusEnum != null) {
                qw.lambda().eq(PaymentOrder::getStatus, statusEnum);
            }
        }

        // 下单时间范围
        if (request.getStartDate() != null) {
            qw.lambda().ge(PaymentOrder::getCreatedAt, request.getStartDate());
        }
        if (request.getEndDate() != null) {
            qw.lambda().le(PaymentOrder::getCreatedAt, request.getEndDate());
        }

        // 支付时间范围
        if (request.getPayStartDate() != null) {
            qw.lambda().ge(PaymentOrder::getPaidAt, request.getPayStartDate());
        }
        if (request.getPayEndDate() != null) {
            qw.lambda().le(PaymentOrder::getPaidAt, request.getPayEndDate());
        }

        qw.lambda().orderByDesc(PaymentOrder::getCreatedAt);
        return qw;
    }

    /**
     * 将订单转换为报表VO
     * 优先使用订单表的快照字段，设备表作为补充
     */
    private RechargeOrderReportVO convertToReportVO(PaymentOrder order, DealerQueryScope queryScope) {
        RechargeOrderReportVO vo = new RechargeOrderReportVO();

        // 订单基本信息
        vo.setOrderId(order.getOrderId());
        vo.setCreatedAt(order.getCreatedAt());
        vo.setDeviceId(order.getDeviceId());
        vo.setStatus(order.getStatus() != null ? order.getStatus().getCode() : null);
        vo.setStatusName(getStatusName(order.getStatus()));

        // 优先使用订单表的快照字段（vendorCode 已改为 dealerCode）
        vo.setVendorCode(order.getDealerCode());
        
        // 经销商名称
        if (order.getDealerCode() != null && !order.getDealerCode().trim().isEmpty()) {
            QueryWrapper<Vendor> vendorQw = new QueryWrapper<>();
            vendorQw.lambda().eq(Vendor::getVendorCode, order.getDealerCode());
            Vendor vendor = vendorRepository.selectOne(vendorQw);
            vo.setVendorName(vendor != null ? vendor.getVendorName() : "");
        }

        // 通过设备表获取装机商代码（订单表未快照此字段）
        ManufacturedDevice device = getDeviceByDeviceId(order.getDeviceId());
        vo.setUserRole(determineUserRole(order, device));

        // 套餐信息
        vo.setPlanId(order.getProductId());
        CloudPlan plan = getPlanByPlanId(order.getProductId());
        if (plan != null) {
            vo.setPlanName(plan.getName());
            vo.setPlanType(plan.getType());
            vo.setPlanTypeName(commissionService.getPlanTypeName(plan.getType()));
            vo.setCloudType(getCloudType(plan.getType()));
            vo.setOriginalPrice(MoneyScaleUtil.keepTwoDecimals(plan.getPrice()));
        }

        // 支付信息
        vo.setPayAmount(MoneyScaleUtil.keepTwoDecimals(order.getAmount()));
        vo.setCurrency(order.getCurrency());
        vo.setCurrencyName(getCurrencyName(order.getCurrency()));
        vo.setPaymentMethod(order.getPaymentMethod());
        vo.setPaymentMethodName(getPaymentMethodName(order.getPaymentMethod()));
        vo.setPayOrderId(order.getOrderId());
        vo.setPaidAt(order.getPaidAt());
        vo.setThirdOrderId(order.getThirdOrderId());
        BigDecimal snapshotFeeAmount = order.getFeeAmount();
        BigDecimal snapshotPlanCost = order.getPlanCost();
        BigDecimal snapshotProfitAmount = order.getProfitAmount();

        // 获取分润配置
        PlanCommission commission = getCommissionByPlanId(order.getProductId());
        if (commission != null) {
            // 财务信息
            vo.setPayeeEntity(commission.getPayeeEntity());
            vo.setFeeRateDesc(commissionService.buildFeeDesc(commission));

            // 计算手续费
            BigDecimal feeAmount = snapshotFeeAmount != null
                    ? MoneyScaleUtil.keepTwoDecimals(snapshotFeeAmount)
                    : calculateFee(order.getAmount(), commission);
            vo.setFeeAmount(feeAmount);

            // 套餐返点和成本
            vo.setRebateDesc(commission.getRebateRate() != null ? commission.getRebateRate() + "%" : "-");
            BigDecimal planCost = snapshotPlanCost != null
                    ? MoneyScaleUtil.keepTwoDecimals(snapshotPlanCost)
                    : MoneyScaleUtil.keepTwoDecimals(commission.getPlanCost() != null ? commission.getPlanCost() : BigDecimal.ZERO);
            vo.setPlanCost(planCost);

            // 计算可分润金额
            BigDecimal profitAmount = snapshotProfitAmount != null
                    ? MoneyScaleUtil.keepTwoDecimals(snapshotProfitAmount)
                    : calculateProfitAmount(order.getAmount(), feeAmount, planCost, commission);
            vo.setProfitAmount(profitAmount);

            // 分润模式
            vo.setProfitMode(commission.getProfitMode() != null ? commission.getProfitMode().getCode() : null);
            vo.setProfitModeName(commissionService.getProfitModeName(commission.getProfitMode()));

            // 分润比例和金额（现在从订单表的快照字段读取）
            // 装机商分润：使用订单表字段
            BigDecimal installerRate = order.getInstallerRate() != null ? order.getInstallerRate() : order.getCommissionRate();
            BigDecimal installerAmount = order.getInstallerAmount();
            vo.setInstallerRateDesc(installerRate != null ? installerRate + "%" : "0%");
            vo.setInstallerAmount(installerAmount != null ? MoneyScaleUtil.keepTwoDecimals(installerAmount) : BigDecimal.ZERO);

            // 经销商分润：优先按 device_dealer 链路计算当前经销商实得，未命中时回落订单快照
            DealerCommissionDisplay dealerDisplay = resolveDealerCommissionDisplay(order, queryScope);
            vo.setLevel1RateDesc(dealerDisplay.getRate() != null ? dealerDisplay.getRate() + "%" : "0%");
            vo.setLevel1Amount(dealerDisplay.getAmount() != null ? MoneyScaleUtil.keepTwoDecimals(dealerDisplay.getAmount()) : BigDecimal.ZERO);

            // 二级经销商分润（已废弃，统一显示为0）
            vo.setLevel2RateDesc("0%");
            vo.setLevel2Amount(BigDecimal.ZERO);
        } else {
            // 无分润配置时的默认值
            vo.setPayeeEntity("-");
            vo.setFeeRateDesc("-");
            vo.setFeeAmount(snapshotFeeAmount != null ? MoneyScaleUtil.keepTwoDecimals(snapshotFeeAmount) : BigDecimal.ZERO);
            vo.setRebateDesc("-");
            vo.setPlanCost(snapshotPlanCost != null ? MoneyScaleUtil.keepTwoDecimals(snapshotPlanCost) : BigDecimal.ZERO);
            vo.setProfitAmount(snapshotProfitAmount != null ? MoneyScaleUtil.keepTwoDecimals(snapshotProfitAmount) : BigDecimal.ZERO);
            vo.setProfitMode("-");
            vo.setProfitModeName("-");
            vo.setInstallerRateDesc("0%");
            vo.setInstallerAmount(BigDecimal.ZERO);
            vo.setLevel1RateDesc("0%");
            vo.setLevel1Amount(BigDecimal.ZERO);
            vo.setLevel2RateDesc("0%");
            vo.setLevel2Amount(BigDecimal.ZERO);
        }

        return vo;
    }

    /**
     * 计算手续费
     */
    private BigDecimal calculateFee(BigDecimal amount, PlanCommission commission) {
        if (amount == null || commission == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal fee = BigDecimal.ZERO;
        BigDecimal feeRate = commission.getFeeRate();
        BigDecimal feeFixed = commission.getFeeFixed();
        CommissionFeeType feeType = commission.getFeeType();
        if (feeType == null) {
            feeType = (feeFixed != null && feeFixed.compareTo(BigDecimal.ZERO) > 0)
                    ? CommissionFeeType.MIXED : CommissionFeeType.FIXED;
        }

        if (feeRate != null) {
            fee = MoneyScaleUtil.percentOf(amount, feeRate);
        }
        if (CommissionFeeType.MIXED == feeType && feeFixed != null) {
            fee = fee.add(feeFixed);
        }

        return MoneyScaleUtil.keepTwoDecimals(fee);
    }

    /**
     * 计算可分润金额
     */
    private BigDecimal calculateProfitAmount(BigDecimal amount, BigDecimal fee, BigDecimal planCost, PlanCommission commission) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }

        CommissionProfitMode profitMode = commission != null && commission.getProfitMode() != null
                ? commission.getProfitMode() : CommissionProfitMode.PROFIT;

        // PROFIT: payment - fee - planCost; REVENUE: payment - fee
        BigDecimal profit = amount.subtract(fee != null ? fee : BigDecimal.ZERO);
        if (CommissionProfitMode.REVENUE != profitMode) {
            profit = profit.subtract(planCost != null ? planCost : BigDecimal.ZERO);
        }

        return profit.compareTo(BigDecimal.ZERO) > 0 ? MoneyScaleUtil.keepTwoDecimals(profit) : BigDecimal.ZERO;
    }

    /**
     * 计算分润金额
     */
    private BigDecimal calculateShareAmount(BigDecimal profitAmount, BigDecimal rate) {
        if (profitAmount == null || rate == null || rate.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return MoneyScaleUtil.percentOf(profitAmount, rate);
    }

    private List<String> resolveDealerDeviceIds(String dealerCode) {
        if (dealerCode == null || dealerCode.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<String> deviceIds = deviceDealerRepository.listDeviceIdsByDealerCode(dealerCode);
            return deviceIds != null ? deviceIds : Collections.emptyList();
        } catch (Exception e) {
            log.debug("查询经销商关联设备失败: dealerCode={}, error={}", dealerCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    private DealerCommissionDisplay resolveDealerCommissionDisplay(PaymentOrder order, DealerQueryScope queryScope) {
        BigDecimal fallbackRate = order.getDealerRate() != null ? order.getDealerRate() : BigDecimal.ZERO;
        BigDecimal fallbackAmount = order.getDealerAmount() != null ? order.getDealerAmount() : BigDecimal.ZERO;

        if (order.getDeviceId() == null || order.getDeviceId().trim().isEmpty() || fallbackAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return new DealerCommissionDisplay(fallbackRate, fallbackAmount);
        }

        List<DeviceDealer> dealerChain;
        try {
            dealerChain = deviceDealerRepository.getDealerChainByDeviceId(order.getDeviceId());
        } catch (Exception e) {
            log.debug("查询设备经销商链路失败: deviceId={}, error={}", order.getDeviceId(), e.getMessage());
            return new DealerCommissionDisplay(fallbackRate, fallbackAmount);
        }

        if (dealerChain == null || dealerChain.isEmpty()) {
            return new DealerCommissionDisplay(fallbackRate, fallbackAmount);
        }

        BigDecimal totalSubRate = BigDecimal.ZERO;
        for (int i = 1; i < dealerChain.size(); i++) {
            BigDecimal subRate = dealerChain.get(i).getCommissionRate() != null ? dealerChain.get(i).getCommissionRate() : BigDecimal.ZERO;
            totalSubRate = totalSubRate.add(subRate);
        }

        for (int i = 0; i < dealerChain.size(); i++) {
            DeviceDealer chainNode = dealerChain.get(i);
            if (!isCurrentDealer(order, chainNode, queryScope)) {
                continue;
            }

            BigDecimal chainRate = chainNode.getCommissionRate() != null ? chainNode.getCommissionRate() : BigDecimal.ZERO;
            if (i == 0) {
                chainRate = HUNDRED.subtract(totalSubRate);
            }
            if (chainRate.compareTo(BigDecimal.ZERO) < 0) {
                chainRate = BigDecimal.ZERO;
            }

            BigDecimal actualAmount = MoneyScaleUtil.percentOf(fallbackAmount, chainRate);
            BigDecimal actualRate = MoneyScaleUtil.percentOf(fallbackRate, chainRate);
            return new DealerCommissionDisplay(actualRate, actualAmount);
        }

        return new DealerCommissionDisplay(fallbackRate, fallbackAmount);
    }

    private boolean isCurrentDealer(PaymentOrder order, DeviceDealer chainNode, DealerQueryScope queryScope) {
        if (queryScope != null && queryScope.getDealerCode() != null && chainNode.getDealerCode() != null
                && queryScope.getDealerCode().trim().equalsIgnoreCase(chainNode.getDealerCode().trim())) {
            return true;
        }
        if (queryScope != null && queryScope.getDealerId() != null && chainNode.getDealerId() != null
                && queryScope.getDealerId().equals(chainNode.getDealerId())) {
            return true;
        }
        if (order.getDealerId() != null && chainNode.getDealerId() != null && order.getDealerId().equals(chainNode.getDealerId())) {
            return true;
        }
        if (order.getDealerCode() == null || chainNode.getDealerCode() == null) {
            return false;
        }
        return order.getDealerCode().trim().equalsIgnoreCase(chainNode.getDealerCode().trim());
    }

    private static class DealerQueryScope {
        private final String installerCode;
        private final String dealerCode;
        private final Long dealerId;
        private final List<String> dealerDeviceIds;
        private final boolean needOrCondition;

        private DealerQueryScope(String installerCode, String dealerCode, Long dealerId,
                                 List<String> dealerDeviceIds, boolean needOrCondition) {
            this.installerCode = installerCode;
            this.dealerCode = dealerCode;
            this.dealerId = dealerId;
            this.dealerDeviceIds = dealerDeviceIds != null ? dealerDeviceIds : Collections.emptyList();
            this.needOrCondition = needOrCondition;
        }

        public String getInstallerCode() {
            return installerCode;
        }

        public String getDealerCode() {
            return dealerCode;
        }

        public Long getDealerId() {
            return dealerId;
        }

        public List<String> getDealerDeviceIds() {
            return dealerDeviceIds;
        }

        public boolean isNeedOrCondition() {
            return needOrCondition;
        }
    }

    private static class DealerCommissionDisplay {
        private final BigDecimal rate;
        private final BigDecimal amount;

        private DealerCommissionDisplay(BigDecimal rate, BigDecimal amount) {
            this.rate = rate;
            this.amount = amount;
        }

        public BigDecimal getRate() {
            return rate;
        }

        public BigDecimal getAmount() {
            return amount;
        }
    }

    /**
     * 根据订单快照和设备信息确定用户身份
     */
    private String determineUserRole(PaymentOrder order, ManufacturedDevice device) {
        List<String> roles = new ArrayList<>();

        // 有装机商代码（从设备表获取）
        if (device != null && device.getAssemblerCode() != null && !device.getAssemblerCode().trim().isEmpty()) {
            roles.add("装机商");
        }

        // 有经销商代码（从订单快照获取，vendorCode 已改为 dealerCode）
        if (order.getDealerCode() != null && !order.getDealerCode().trim().isEmpty()) {
            roles.add("经销商");
        }

        return roles.isEmpty() ? "普通用户" : String.join("+", roles);
    }

    /**
     * 根据设备ID获取设备信息
     */
    private ManufacturedDevice getDeviceByDeviceId(String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return null;
        }
        QueryWrapper<ManufacturedDevice> qw = new QueryWrapper<>();
        qw.lambda().eq(ManufacturedDevice::getDeviceId, deviceId);
        return deviceRepository.selectOne(qw);
    }

    /**
     * 获取云存类型
     */
    private String getCloudType(String planType) {
        if ("traffic".equals(planType)) {
            return "流量";
        }
        return "云存";
    }

    /**
     * 获取币种名称
     */
    private String getCurrencyName(String currency) {
        if (currency == null) {
            return "未知";
        }
        switch (currency.toUpperCase()) {
            case "CNY":
                return "人民币";
            case "USD":
                return "美元";
            default:
                return currency;
        }
    }

    /**
     * 获取支付方式名称
     */
    private String getPaymentMethodName(String method) {
        if (method == null) {
            return "未知";
        }
        switch (method.toLowerCase()) {
            case "wechat":
                return "微信支付";
            case "paypal":
                return "PayPal支付";
            case "apple":
                return "苹果支付";
            case "google":
                return "谷歌支付";
            default:
                return method;
        }
    }

    /**
     * 获取订单状态名称
     */
    private String getStatusName(PaymentOrderStatus status) {
        if (status == null) {
            return "未知";
        }
        return status.getDescription();
    }

    /**
     * 根据planId获取套餐
     */
    private CloudPlan getPlanByPlanId(String planId) {
        if (planId == null || planId.trim().isEmpty()) {
            return null;
        }
        QueryWrapper<CloudPlan> qw = new QueryWrapper<>();
        qw.lambda().eq(CloudPlan::getPlanId, planId);
        return planRepository.selectOne(qw);
    }

    /**
     * 根据planId获取分润配置
     */
    private PlanCommission getCommissionByPlanId(String planId) {
        if (planId == null || planId.trim().isEmpty()) {
            return null;
        }
        QueryWrapper<PlanCommission> qw = new QueryWrapper<>();
        qw.lambda().eq(PlanCommission::getPlanId, planId);
        return commissionRepository.selectOne(qw);
    }

    /**
     * 创建文本单元格
     */
    private void createCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    /**
     * 创建金额单元格
     */
    private void createMoneyCell(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(MoneyScaleUtil.keepTwoDecimals(value).doubleValue());
        cell.setCellStyle(style);
    }
}
