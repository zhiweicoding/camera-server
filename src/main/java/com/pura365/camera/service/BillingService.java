package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.domain.*;
import com.pura365.camera.enums.PaymentOrderStatus;
import com.pura365.camera.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
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

    @Autowired
    private PaymentOrderRepository orderRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private SalesmanRepository salesmanRepository;

    @Autowired
    private InstallerRepository installerRepository;

    @Autowired
    private ManufacturedDeviceRepository deviceRepository;

    @Autowired
    private CloudPlanRepository cloudPlanRepository;

    /**
     * 获取经销商账单汇总统计
     * @param vendorCode 经销商代码（可选，不传则统计所有）
     * @param startDate 开始日期
     * @param endDate 结束日期
     */
    public Map<String, Object> getVendorBillingSummary(String vendorCode, Date startDate, Date endDate) {
        QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();
        qw.lambda().eq(PaymentOrder::getStatus, PaymentOrderStatus.PAID);
        if (vendorCode != null && !vendorCode.trim().isEmpty()) {
            qw.lambda().eq(PaymentOrder::getVendorCode, vendorCode);
        }
        if (startDate != null) {
            qw.lambda().ge(PaymentOrder::getPaidAt, startDate);
        }
        if (endDate != null) {
            qw.lambda().le(PaymentOrder::getPaidAt, endDate);
        }

        List<PaymentOrder> orders = orderRepository.selectList(qw);

        // 按经销商分组统计
        Map<String, Map<String, Object>> vendorStats = new HashMap<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalVendorAmount = BigDecimal.ZERO;
        int totalOrders = 0;

        for (PaymentOrder order : orders) {
            String vCode = order.getVendorCode();
            if (vCode == null) vCode = "未分配";

            Map<String, Object> stat = vendorStats.computeIfAbsent(vCode, k -> {
                Map<String, Object> s = new HashMap<>();
                s.put("vendorCode", k);
                s.put("orderCount", 0);
                s.put("totalAmount", BigDecimal.ZERO);
                s.put("vendorAmount", BigDecimal.ZERO);
                return s;
            });

            stat.put("orderCount", (Integer) stat.get("orderCount") + 1);
            stat.put("totalAmount", ((BigDecimal) stat.get("totalAmount")).add(order.getAmount() != null ? order.getAmount() : BigDecimal.ZERO));
            stat.put("vendorAmount", ((BigDecimal) stat.get("vendorAmount")).add(order.getVendorAmount() != null ? order.getVendorAmount() : BigDecimal.ZERO));

            totalAmount = totalAmount.add(order.getAmount() != null ? order.getAmount() : BigDecimal.ZERO);
            totalVendorAmount = totalVendorAmount.add(order.getVendorAmount() != null ? order.getVendorAmount() : BigDecimal.ZERO);
            totalOrders++;
        }

        // 补充经销商名称
        List<Map<String, Object>> vendorList = new ArrayList<>(vendorStats.values());
        for (Map<String, Object> stat : vendorList) {
            String vCode = (String) stat.get("vendorCode");
            if (!"未分配".equals(vCode)) {
                QueryWrapper<Vendor> vqw = new QueryWrapper<>();
                vqw.lambda().eq(Vendor::getVendorCode, vCode);
                Vendor vendor = vendorRepository.selectOne(vqw);
                stat.put("vendorName", vendor != null ? vendor.getVendorName() : "未知");
            } else {
                stat.put("vendorName", "未分配");
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", vendorList);
        result.put("totalOrders", totalOrders);
        result.put("totalAmount", totalAmount);
        result.put("totalVendorAmount", totalVendorAmount);
        return result;
    }

    /**
     * 获取装机商账单汇总统计
     * 兼容新旧数据：优先使用 installer_id/installer_code，回退到 vendor_code
     * @param installerId 装机商ID（可选）
     * @param installerCode 装机商代码（可选）
     * @param startDate 开始日期
     * @param endDate 结束日期
     */
    public Map<String, Object> getInstallerBillingSummary(Long installerId, String installerCode, Date startDate, Date endDate) {
        QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();
        qw.lambda().eq(PaymentOrder::getStatus, PaymentOrderStatus.PAID);
        if (installerId != null) {
            qw.lambda().eq(PaymentOrder::getInstallerId, installerId);
        }
        if (installerCode != null && !installerCode.trim().isEmpty()) {
            // 同时支持新字段(installerCode)和旧字段(vendorCode)
            qw.and(w -> w.lambda()
                    .eq(PaymentOrder::getInstallerCode, installerCode)
                    .or()
                    .eq(PaymentOrder::getVendorCode, installerCode));
        }
        if (startDate != null) {
            qw.lambda().ge(PaymentOrder::getPaidAt, startDate);
        }
        if (endDate != null) {
            qw.lambda().le(PaymentOrder::getPaidAt, endDate);
        }

        List<PaymentOrder> orders = orderRepository.selectList(qw);

        // 按装机商代码分组统计（兼容新旧字段）
        Map<String, Map<String, Object>> installerStats = new HashMap<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalProfitAmount = BigDecimal.ZERO;
        BigDecimal totalInstallerAmount = BigDecimal.ZERO;
        BigDecimal totalDealerAmount = BigDecimal.ZERO;
        Set<String> deviceIds = new HashSet<>();
        int totalOrders = 0;

        for (PaymentOrder order : orders) {
            // 优先使用 installerCode，回退到 vendorCode
            String code = order.getInstallerCode();
            if (code == null || code.trim().isEmpty()) {
                code = order.getVendorCode();
            }
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
                return s;
            });

            stat.put("orderCount", (Integer) stat.get("orderCount") + 1);
            stat.put("totalAmount", ((BigDecimal) stat.get("totalAmount")).add(order.getAmount() != null ? order.getAmount() : BigDecimal.ZERO));
            stat.put("installerAmount", ((BigDecimal) stat.get("installerAmount")).add(order.getInstallerAmount() != null ? order.getInstallerAmount() : BigDecimal.ZERO));

            totalAmount = totalAmount.add(order.getAmount() != null ? order.getAmount() : BigDecimal.ZERO);
            totalCost = totalCost.add(order.getPlanCost() != null ? order.getPlanCost() : BigDecimal.ZERO);
            totalProfitAmount = totalProfitAmount.add(order.getProfitAmount() != null ? order.getProfitAmount() : BigDecimal.ZERO);
            totalInstallerAmount = totalInstallerAmount.add(order.getInstallerAmount() != null ? order.getInstallerAmount() : BigDecimal.ZERO);
            totalDealerAmount = totalDealerAmount.add(order.getDealerAmount() != null ? order.getDealerAmount() : BigDecimal.ZERO);
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
            // 先通过 installerId 查
            if (iid != null) {
                Installer installer = installerRepository.selectById(iid);
                if (installer != null) {
                    installerName = installer.getInstallerName();
                    if (stat.get("installerCode") == null || ((String) stat.get("installerCode")).isEmpty()) {
                        stat.put("installerCode", installer.getInstallerCode());
                    }
                }
            }
            // 再通过 installerCode 查
            if (installerName == null && code != null && !"未分配".equals(code)) {
                QueryWrapper<Installer> iqw = new QueryWrapper<>();
                iqw.lambda().eq(Installer::getInstallerCode, code);
                Installer installer = installerRepository.selectOne(iqw);
                if (installer != null) {
                    installerName = installer.getInstallerName();
                    stat.put("installerId", installer.getId());
                } else {
                    // 尝试用 vendorCode 查 Vendor 表（旧数据兼容）
                    QueryWrapper<Vendor> vqw = new QueryWrapper<>();
                    vqw.lambda().eq(Vendor::getVendorCode, code);
                    Vendor vendor = vendorRepository.selectOne(vqw);
                    if (vendor != null) {
                        installerName = vendor.getVendorName();
                    }
                }
            }
            stat.put("installerName", installerName != null ? installerName : ("未分配".equals(code) ? "未分配" : "未知"));
        }

        // 计算剩余利润 = 可分润金额 - 装机商分润 - 经销商分润
        BigDecimal totalRemainingProfit = totalProfitAmount.subtract(totalInstallerAmount).subtract(totalDealerAmount);

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
        return result;
    }

    /**
     * 获取经销商账单汇总统计
     * 兼容新旧数据：优先使用 dealer_id，回退到 salesman_id
     * @param installerCode 装机商代码（可选）
     * @param dealerId 经销商ID（可选）
     * @param startDate 开始日期
     * @param endDate 结束日期
     */
    public Map<String, Object> getDealerBillingSummary(String installerCode, Long dealerId, Date startDate, Date endDate) {
        QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();
        qw.lambda().eq(PaymentOrder::getStatus, PaymentOrderStatus.PAID);
        if (installerCode != null && !installerCode.trim().isEmpty()) {
            // 同时支持新字段(installerCode)和旧字段(vendorCode)
            qw.and(w -> w.lambda()
                    .eq(PaymentOrder::getInstallerCode, installerCode)
                    .or()
                    .eq(PaymentOrder::getVendorCode, installerCode));
        }
        if (dealerId != null) {
            // 同时支持新字段(dealerId)和旧字段(salesmanId)
            qw.and(w -> w.lambda()
                    .eq(PaymentOrder::getDealerId, dealerId)
                    .or()
                    .eq(PaymentOrder::getSalesmanId, dealerId));
        }
        if (startDate != null) {
            qw.lambda().ge(PaymentOrder::getPaidAt, startDate);
        }
        if (endDate != null) {
            qw.lambda().le(PaymentOrder::getPaidAt, endDate);
        }

        List<PaymentOrder> orders = orderRepository.selectList(qw);

        // 按经销商分组统计（兼容新旧字段）
        Map<String, Map<String, Object>> dealerStats = new HashMap<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalProfitAmount = BigDecimal.ZERO;
        BigDecimal totalInstallerAmount = BigDecimal.ZERO;
        BigDecimal totalDealerAmount = BigDecimal.ZERO;
        Set<String> deviceIds = new HashSet<>();
        int totalOrders = 0;

        for (PaymentOrder order : orders) {
            // 优先使用 dealerId，回退到 salesmanId
            Long did = order.getDealerId();
            if (did == null) {
                did = order.getSalesmanId();
            }
            // 用字符串做 key（兼容 null 情况）
            String key = did != null ? String.valueOf(did) : "未分配";

            // 获取装机商代码
            String iCode = order.getInstallerCode();
            if (iCode == null || iCode.trim().isEmpty()) {
                iCode = order.getVendorCode();
            }

            final Long finalDid = did;
            final String finalICode = iCode;
            Map<String, Object> stat = dealerStats.computeIfAbsent(key, k -> {
                Map<String, Object> s = new HashMap<>();
                s.put("dealerId", finalDid);
                s.put("dealerName", order.getSalesmanName());
                s.put("installerCode", finalICode);
                s.put("orderCount", 0);
                s.put("totalAmount", BigDecimal.ZERO);
                s.put("dealerAmount", BigDecimal.ZERO);
                return s;
            });

            stat.put("orderCount", (Integer) stat.get("orderCount") + 1);
            // 优先使用 dealerAmount，回退到 salesmanAmount
            BigDecimal dAmt = order.getDealerAmount();
            if (dAmt == null) {
                dAmt = order.getSalesmanAmount();
            }
            stat.put("totalAmount", ((BigDecimal) stat.get("totalAmount")).add(order.getAmount() != null ? order.getAmount() : BigDecimal.ZERO));
            stat.put("dealerAmount", ((BigDecimal) stat.get("dealerAmount")).add(dAmt != null ? dAmt : BigDecimal.ZERO));

            totalAmount = totalAmount.add(order.getAmount() != null ? order.getAmount() : BigDecimal.ZERO);
            totalCost = totalCost.add(order.getPlanCost() != null ? order.getPlanCost() : BigDecimal.ZERO);
            totalProfitAmount = totalProfitAmount.add(order.getProfitAmount() != null ? order.getProfitAmount() : BigDecimal.ZERO);
            totalInstallerAmount = totalInstallerAmount.add(order.getInstallerAmount() != null ? order.getInstallerAmount() : BigDecimal.ZERO);
            BigDecimal orderDealerAmt = order.getDealerAmount() != null ? order.getDealerAmount() : (order.getSalesmanAmount() != null ? order.getSalesmanAmount() : BigDecimal.ZERO);
            totalDealerAmount = totalDealerAmount.add(orderDealerAmt);
            if (order.getDeviceId() != null) {
                deviceIds.add(order.getDeviceId());
            }
            totalOrders++;
        }

        List<Map<String, Object>> dealerList = new ArrayList<>(dealerStats.values());

        // 计算剩余利润
        BigDecimal totalRemainingProfit = totalProfitAmount.subtract(totalInstallerAmount).subtract(totalDealerAmount);

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
        return result;
    }

    /**
     * 获取业务员账单汇总统计（已废弃，请使用 getDealerBillingSummary）
     * @deprecated 使用 getDealerBillingSummary 替代
     */
    @Deprecated
    public Map<String, Object> getSalesmanBillingSummary(String vendorCode, Long salesmanId, Date startDate, Date endDate) {
        QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();
        qw.lambda().eq(PaymentOrder::getStatus, PaymentOrderStatus.PAID)
                .isNotNull(PaymentOrder::getSalesmanId);
        if (vendorCode != null && !vendorCode.trim().isEmpty()) {
            qw.lambda().eq(PaymentOrder::getVendorCode, vendorCode);
        }
        if (salesmanId != null) {
            qw.lambda().eq(PaymentOrder::getSalesmanId, salesmanId);
        }
        if (startDate != null) {
            qw.lambda().ge(PaymentOrder::getPaidAt, startDate);
        }
        if (endDate != null) {
            qw.lambda().le(PaymentOrder::getPaidAt, endDate);
        }

        List<PaymentOrder> orders = orderRepository.selectList(qw);

        // 按业务员分组统计
        Map<Long, Map<String, Object>> salesmanStats = new HashMap<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalSalesmanAmount = BigDecimal.ZERO;
        int totalOrders = 0;

        for (PaymentOrder order : orders) {
            Long sId = order.getSalesmanId();
            if (sId == null) continue;

            Map<String, Object> stat = salesmanStats.computeIfAbsent(sId, k -> {
                Map<String, Object> s = new HashMap<>();
                s.put("salesmanId", k);
                s.put("salesmanName", order.getSalesmanName());
                s.put("vendorCode", order.getVendorCode());
                s.put("orderCount", 0);
                s.put("totalAmount", BigDecimal.ZERO);
                s.put("salesmanAmount", BigDecimal.ZERO);
                return s;
            });

            stat.put("orderCount", (Integer) stat.get("orderCount") + 1);
            stat.put("totalAmount", ((BigDecimal) stat.get("totalAmount")).add(order.getAmount() != null ? order.getAmount() : BigDecimal.ZERO));
            stat.put("salesmanAmount", ((BigDecimal) stat.get("salesmanAmount")).add(order.getSalesmanAmount() != null ? order.getSalesmanAmount() : BigDecimal.ZERO));

            totalAmount = totalAmount.add(order.getAmount() != null ? order.getAmount() : BigDecimal.ZERO);
            totalSalesmanAmount = totalSalesmanAmount.add(order.getSalesmanAmount() != null ? order.getSalesmanAmount() : BigDecimal.ZERO);
            totalOrders++;
        }

        List<Map<String, Object>> salesmanList = new ArrayList<>(salesmanStats.values());

        Map<String, Object> result = new HashMap<>();
        result.put("list", salesmanList);
        result.put("totalOrders", totalOrders);
        result.put("totalAmount", totalAmount);
        result.put("totalSalesmanAmount", totalSalesmanAmount);
        return result;
    }

    /**
     * 获取订单明细列表（用于导出）
     * @param vendorCode 经销商代码（可选）
     * @param salesmanId 业务员ID（可选）
     * @param startDate 开始日期
     * @param endDate 结束日期
     */
    public List<Map<String, Object>> getOrderDetails(String vendorCode, Long salesmanId, Date startDate, Date endDate) {
        QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();
        qw.lambda().eq(PaymentOrder::getStatus, PaymentOrderStatus.PAID);
        if (vendorCode != null && !vendorCode.trim().isEmpty()) {
            qw.lambda().eq(PaymentOrder::getVendorCode, vendorCode);
        }
        if (salesmanId != null) {
            qw.lambda().eq(PaymentOrder::getSalesmanId, salesmanId);
        }
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
            row.put("vendorCode", order.getVendorCode());
            row.put("salesmanId", order.getSalesmanId());
            row.put("salesmanName", order.getSalesmanName());
            row.put("productType", order.getProductType());
            row.put("productId", order.getProductId());
            row.put("amount", order.getAmount());
            row.put("commissionRate", order.getCommissionRate());
            row.put("salesmanAmount", order.getSalesmanAmount());
            row.put("vendorAmount", order.getVendorAmount());
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
     */
    public Map<String, Object> listOrders(Integer page, Integer size, String vendorCode, Long salesmanId, 
                                          String deviceId, String status, Date startDate, Date endDate) {
        QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();
        if (vendorCode != null && !vendorCode.trim().isEmpty()) {
            qw.lambda().eq(PaymentOrder::getVendorCode, vendorCode);
        }
        if (salesmanId != null) {
            qw.lambda().eq(PaymentOrder::getSalesmanId, salesmanId);
        }
        if (deviceId != null && !deviceId.trim().isEmpty()) {
            qw.lambda().like(PaymentOrder::getDeviceId, deviceId);
        }
        if (status != null && !status.trim().isEmpty()) {
            PaymentOrderStatus statusEnum = PaymentOrderStatus.fromCode(status);
            if (statusEnum != null) {
                qw.lambda().eq(PaymentOrder::getStatus, statusEnum);
            }
        }
        if (startDate != null) {
            qw.lambda().ge(PaymentOrder::getPaidAt, startDate);
        }
        if (endDate != null) {
            qw.lambda().le(PaymentOrder::getPaidAt, endDate);
        }
        qw.lambda().orderByDesc(PaymentOrder::getCreatedAt);

        // 分页查询
        int offset = (page - 1) * size;
        qw.last("LIMIT " + offset + ", " + size);
        List<PaymentOrder> list = orderRepository.selectList(qw);

        // 查询总数
        QueryWrapper<PaymentOrder> countQw = new QueryWrapper<>();
        if (vendorCode != null && !vendorCode.trim().isEmpty()) {
            countQw.lambda().eq(PaymentOrder::getVendorCode, vendorCode);
        }
        if (salesmanId != null) {
            countQw.lambda().eq(PaymentOrder::getSalesmanId, salesmanId);
        }
        if (deviceId != null && !deviceId.trim().isEmpty()) {
            countQw.lambda().like(PaymentOrder::getDeviceId, deviceId);
        }
        if (status != null && !status.trim().isEmpty()) {
            PaymentOrderStatus statusEnum = PaymentOrderStatus.fromCode(status);
            if (statusEnum != null) {
                countQw.lambda().eq(PaymentOrder::getStatus, statusEnum);
            }
        }
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

    private static final int EXCEL_BATCH_SIZE = 10000;

    /**
     * 导出充值明细Excel
     * 如果数据超过1万条，分成多个Excel文件并打包成zip
     * @return Map包含 "isZip" (boolean) 和 "data" (byte[])
     */
    public Map<String, Object> exportBillingDetailExcel(String vendorCode, Long salesmanId, String deviceId, Date startDate, Date endDate) throws IOException {
        log.info("开始导出充值明细Excel: vendorCode={}, salesmanId={}, deviceId={}, startDate={}, endDate={}", vendorCode, salesmanId, deviceId, startDate, endDate);

        // 查询所有符合条件的订单
        QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();
        qw.lambda().eq(PaymentOrder::getStatus, PaymentOrderStatus.PAID);
        if (vendorCode != null && !vendorCode.trim().isEmpty()) {
            qw.lambda().eq(PaymentOrder::getInstallerCode, vendorCode);
        }
        if (salesmanId != null) {
            qw.lambda().eq(PaymentOrder::getDealerId, salesmanId);
        }
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
        log.info("查询到充值明细订单数: {}", orders.size());

        Map<String, Object> result = new HashMap<>();

        if (orders.size() <= EXCEL_BATCH_SIZE) {
            // 单个Excel文件
            byte[] excelData = createBillingDetailExcel(orders, null);
            result.put("isZip", false);
            result.put("data", excelData);
            log.info("导出单个Excel文件完成");
        } else {
            // 分片打包成zip
            byte[] zipData = createBillingDetailZip(orders);
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
                createTextCell(row, col++, order.getSalesmanId() != null ? String.valueOf(order.getSalesmanId()) : "", dataStyle);
                // 经销商名称
                createTextCell(row, col++, order.getSalesmanName(), dataStyle);
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
                createMoneyCell(row, col++, order.getSalesmanAmount(), moneyStyle);
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
     * 获取经销商下设备的支付统计
     * @param vendorCode 经销商代码
     * @param startDate 开始日期
     * @param endDate 结束日期
     */
    public Map<String, Object> getDevicePaymentStats(String vendorCode, Date startDate, Date endDate) {
        // 获取经销商下的所有设备
        QueryWrapper<ManufacturedDevice> deviceQw = new QueryWrapper<>();
        deviceQw.lambda().eq(ManufacturedDevice::getVendorCode, vendorCode);
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
}
