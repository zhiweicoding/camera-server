package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.domain.*;
import com.pura365.camera.enums.CloudPlanType;
import com.pura365.camera.enums.CommissionFeeType;
import com.pura365.camera.enums.PaymentOrderStatus;
import com.pura365.camera.model.report.PageResult;
import com.pura365.camera.model.report.RechargeOrderQueryRequest;
import com.pura365.camera.model.report.RechargeOrderReportVO;
import com.pura365.camera.repository.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

        // 直接使用订单表的快照字段查询
        QueryWrapper<PaymentOrder> qw = buildQueryWrapper(request);

        // 分页查询
        int offset = (request.getPage() - 1) * request.getSize();
        qw.last("LIMIT " + offset + ", " + request.getSize());
        List<PaymentOrder> orders = orderRepository.selectList(qw);

        // 查询总数
        QueryWrapper<PaymentOrder> countQw = buildQueryWrapper(request);
        long total = orderRepository.selectCount(countQw);

        // 转换为报表VO
        List<RechargeOrderReportVO> voList = new ArrayList<>();
        for (PaymentOrder order : orders) {
            voList.add(convertToReportVO(order));
        }

        log.info("查询充值订单报表完成: total={}, listSize={}", total, voList.size());
        return PageResult.of(voList, total, request.getPage(), request.getSize());
    }

    /**
     * 查询所有符合条件的订单（用于导出，不分页）
     */
    public List<RechargeOrderReportVO> queryAllOrders(RechargeOrderQueryRequest request) {
        log.info("查询充值订单用于导出");

        // 直接使用订单表的快照字段查询
        QueryWrapper<PaymentOrder> qw = buildQueryWrapper(request);
        qw.lambda().orderByDesc(PaymentOrder::getCreatedAt);

        List<PaymentOrder> orders = orderRepository.selectList(qw);

        List<RechargeOrderReportVO> voList = new ArrayList<>();
        for (PaymentOrder order : orders) {
            voList.add(convertToReportVO(order));
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

    /**
     * 构建查询条件
     * 直接使用订单表的快照字段进行筛选
     */
    private QueryWrapper<PaymentOrder> buildQueryWrapper(RechargeOrderQueryRequest request) {
        QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();

        if (request.getOrderId() != null && !request.getOrderId().trim().isEmpty()) {
            qw.lambda().like(PaymentOrder::getOrderId, request.getOrderId());
        }
        if (request.getDeviceId() != null && !request.getDeviceId().trim().isEmpty()) {
            qw.lambda().like(PaymentOrder::getDeviceId, request.getDeviceId());
        }
        
        // 直接使用订单表的快照字段查询
        if (request.getVendorCode() != null && !request.getVendorCode().trim().isEmpty()) {
            qw.lambda().eq(PaymentOrder::getVendorCode, request.getVendorCode());
        }
        if (request.getSalesmanId() != null) {
            qw.lambda().eq(PaymentOrder::getSalesmanId, request.getSalesmanId());
        }
        
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
    private RechargeOrderReportVO convertToReportVO(PaymentOrder order) {
        RechargeOrderReportVO vo = new RechargeOrderReportVO();

        // 订单基本信息
        vo.setOrderId(order.getOrderId());
        vo.setCreatedAt(order.getCreatedAt());
        vo.setDeviceId(order.getDeviceId());
        vo.setStatus(order.getStatus() != null ? order.getStatus().getCode() : null);
        vo.setStatusName(getStatusName(order.getStatus()));

        // 优先使用订单表的快照字段
        vo.setVendorCode(order.getVendorCode());
        
        // 经销商名称
        if (order.getVendorCode() != null && !order.getVendorCode().trim().isEmpty()) {
            QueryWrapper<Vendor> vendorQw = new QueryWrapper<>();
            vendorQw.lambda().eq(Vendor::getVendorCode, order.getVendorCode());
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
            vo.setPlanType(plan.getType() != null ? plan.getType().getCode() : null);
            vo.setPlanTypeName(commissionService.getPlanTypeName(plan.getType()));
            vo.setCloudType(getCloudType(plan.getType()));
            vo.setOriginalPrice(plan.getPrice());
        }

        // 支付信息
        vo.setPayAmount(order.getAmount());
        vo.setCurrency(order.getCurrency());
        vo.setCurrencyName(getCurrencyName(order.getCurrency()));
        vo.setPaymentMethod(order.getPaymentMethod());
        vo.setPaymentMethodName(getPaymentMethodName(order.getPaymentMethod()));
        vo.setPayOrderId(order.getOrderId());
        vo.setPaidAt(order.getPaidAt());
        vo.setThirdOrderId(order.getThirdOrderId());

        // 获取分润配置
        PlanCommission commission = getCommissionByPlanId(order.getProductId());
        if (commission != null) {
            // 财务信息
            vo.setPayeeEntity(commission.getPayeeEntity());
            vo.setFeeRateDesc(commissionService.buildFeeDesc(commission));

            // 计算手续费
            BigDecimal feeAmount = calculateFee(order.getAmount(), commission);
            vo.setFeeAmount(feeAmount);

            // 套餐返点和成本
            vo.setRebateDesc(commission.getRebateRate() != null ? commission.getRebateRate() + "%" : "-");
            vo.setPlanCost(commission.getPlanCost());

            // 计算可分润金额
            BigDecimal profitAmount = calculateProfitAmount(order.getAmount(), feeAmount, commission);
            vo.setProfitAmount(profitAmount);

            // 分润模式
            vo.setProfitMode(commission.getProfitMode() != null ? commission.getProfitMode().getCode() : null);
            vo.setProfitModeName(commissionService.getProfitModeName(commission.getProfitMode()));

            // 分润比例和金额
            vo.setInstallerRateDesc(commission.getInstallerRate() != null ? commission.getInstallerRate() + "%" : "0%");
            vo.setInstallerAmount(calculateShareAmount(profitAmount, commission.getInstallerRate()));

            vo.setLevel1RateDesc(commission.getLevel1Rate() != null ? commission.getLevel1Rate() + "%" : "0%");
            vo.setLevel1Amount(calculateShareAmount(profitAmount, commission.getLevel1Rate()));

            vo.setLevel2RateDesc(commission.getLevel2Rate() != null ? commission.getLevel2Rate() + "%" : "0%");
            vo.setLevel2Amount(calculateShareAmount(profitAmount, commission.getLevel2Rate()));
        } else {
            // 无分润配置时的默认值
            vo.setPayeeEntity("-");
            vo.setFeeRateDesc("-");
            vo.setFeeAmount(BigDecimal.ZERO);
            vo.setRebateDesc("-");
            vo.setPlanCost(BigDecimal.ZERO);
            vo.setProfitAmount(BigDecimal.ZERO);
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
        CommissionFeeType feeType = commission.getFeeType();
        BigDecimal feeRate = commission.getFeeRate();
        BigDecimal feeFixed = commission.getFeeFixed();

        if (CommissionFeeType.MIXED == feeType && feeRate != null && feeFixed != null) {
            // 混合类型：百分比 + 固定金额
            fee = amount.multiply(feeRate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                    .add(feeFixed);
        } else if (feeRate != null) {
            // 固定比例
            fee = amount.multiply(feeRate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        }

        return fee.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算可分润金额
     */
    private BigDecimal calculateProfitAmount(BigDecimal amount, BigDecimal fee, PlanCommission commission) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }

        // 可分润金额 = 支付金额 - 手续费 - 套餐成本
        BigDecimal profit = amount.subtract(fee != null ? fee : BigDecimal.ZERO);
        if (commission != null && commission.getPlanCost() != null) {
            profit = profit.subtract(commission.getPlanCost());
        }

        return profit.compareTo(BigDecimal.ZERO) > 0 ? profit.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    /**
     * 计算分润金额
     */
    private BigDecimal calculateShareAmount(BigDecimal profitAmount, BigDecimal rate) {
        if (profitAmount == null || rate == null || rate.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return profitAmount.multiply(rate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
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

        // 有经销商代码（从订单快照获取）
        if (order.getVendorCode() != null && !order.getVendorCode().trim().isEmpty()) {
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
    private String getCloudType(CloudPlanType planType) {
        if (CloudPlanType.TRAFFIC == planType) {
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
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        } else {
            cell.setCellValue(0.0);
        }
        cell.setCellStyle(style);
    }
}
