package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.domain.*;
import com.pura365.camera.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

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
    private ManufacturedDeviceRepository deviceRepository;

    /**
     * 获取经销商账单汇总统计
     * @param vendorCode 经销商代码（可选，不传则统计所有）
     * @param startDate 开始日期
     * @param endDate 结束日期
     */
    public Map<String, Object> getVendorBillingSummary(String vendorCode, Date startDate, Date endDate) {
        QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();
        qw.lambda().eq(PaymentOrder::getStatus, "paid");
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
     * 获取业务员账单汇总统计
     * @param vendorCode 经销商代码（可选）
     * @param salesmanId 业务员ID（可选）
     * @param startDate 开始日期
     * @param endDate 结束日期
     */
    public Map<String, Object> getSalesmanBillingSummary(String vendorCode, Long salesmanId, Date startDate, Date endDate) {
        QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();
        qw.lambda().eq(PaymentOrder::getStatus, "paid")
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
        qw.lambda().eq(PaymentOrder::getStatus, "paid");
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
            qw.lambda().eq(PaymentOrder::getStatus, status);
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
            countQw.lambda().eq(PaymentOrder::getStatus, status);
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
        if (!"paid".equals(order.getStatus())) {
            throw new RuntimeException("只有已支付的订单才能退款");
        }
        order.setStatus("refunded");
        order.setRefundAt(new Date());
        order.setRefundReason(reason);
        order.setUpdatedAt(new Date());
        orderRepository.updateById(order);
        log.info("记录退款: orderId={}, reason={}", orderId, reason);
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
                    .eq(PaymentOrder::getStatus, "paid");
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
                stat.put("salesmanId", device.getSalesmanId());
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
