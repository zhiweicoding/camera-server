package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pura365.camera.domain.*;
import com.pura365.camera.enums.TransferStatus;
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
 * 设备转让服务
 * 处理经销商之间的设备转让和多级分润
 */
@Service
public class DeviceTransferService {

    private static final Logger log = LoggerFactory.getLogger(DeviceTransferService.class);

    @Autowired
    private DeviceTransferRepository transferRepository;

    @Autowired
    private DeviceVendorRepository deviceVendorRepository;

    @Autowired
    private ManufacturedDeviceRepository deviceRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private InstallerRepository installerRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 创建设备转让
     * 
     * @param fromVendorId 转出经销商ID（NULL表示装机商直接分配）
     * @param toVendorId 转入经销商ID
     * @param deviceIds 设备ID列表
     * @param commissionRate 分润比例
     * @param remark 备注
     * @return 转让记录
     */
    @Transactional
    public DeviceTransfer createTransfer(Long fromVendorId, Long toVendorId, 
                                         List<String> deviceIds, BigDecimal commissionRate, String remark) {
        // 验证转入经销商
        Vendor toVendor = vendorRepository.selectById(toVendorId);
        if (toVendor == null) {
            throw new RuntimeException("转入经销商不存在");
        }

        // 验证分润比例
        if (commissionRate == null || commissionRate.compareTo(BigDecimal.ZERO) < 0 
                || commissionRate.compareTo(new BigDecimal("100")) > 0) {
            throw new RuntimeException("分润比例必须在0-100之间");
        }

        // 验证设备
        if (deviceIds == null || deviceIds.isEmpty()) {
            throw new RuntimeException("设备列表不能为空");
        }

        Vendor fromVendor = null;
        Installer installer = null;
        int fromLevel = 0;

        if (fromVendorId != null) {
            // 经销商之间转让
            fromVendor = vendorRepository.selectById(fromVendorId);
            if (fromVendor == null) {
                throw new RuntimeException("转出经销商不存在");
            }
            
            // 验证转出经销商和转入经销商属于同一装机商
            if (!fromVendor.getInstallerId().equals(toVendor.getInstallerId())) {
                throw new RuntimeException("转出和转入经销商必须属于同一装机商");
            }
            
            installer = installerRepository.selectById(fromVendor.getInstallerId());
            fromLevel = fromVendor.getLevel() != null ? fromVendor.getLevel() : 1;

            // 验证设备属于转出经销商
            for (String deviceId : deviceIds) {
                DeviceVendor dv = deviceVendorRepository.getCurrentVendor(deviceId);
                if (dv == null || !dv.getVendorId().equals(fromVendorId)) {
                    throw new RuntimeException("设备 " + deviceId + " 不属于转出经销商");
                }
            }
        } else {
            // 装机商直接分配给一级经销商
            installer = installerRepository.selectById(toVendor.getInstallerId());
            if (installer == null) {
                throw new RuntimeException("装机商不存在");
            }

            // 验证设备属于该装机商且未被分配
            for (String deviceId : deviceIds) {
                ManufacturedDevice device = getDeviceByDeviceId(deviceId);
                if (device == null) {
                    throw new RuntimeException("设备 " + deviceId + " 不存在");
                }
                // 检查设备是否已被分配
                DeviceVendor existing = deviceVendorRepository.getCurrentVendor(deviceId);
                if (existing != null) {
                    throw new RuntimeException("设备 " + deviceId + " 已被分配给经销商");
                }
            }
        }

        // 生成转让单号
        String transferNo = generateTransferNo();

        // 创建转让记录
        DeviceTransfer transfer = new DeviceTransfer();
        transfer.setTransferNo(transferNo);
        transfer.setFromVendorId(fromVendorId);
        transfer.setFromVendorCode(fromVendor != null ? fromVendor.getVendorCode() : null);
        transfer.setToVendorId(toVendorId);
        transfer.setToVendorCode(toVendor.getVendorCode());
        transfer.setCommissionRate(commissionRate);
        transfer.setDeviceCount(deviceIds.size());
        transfer.setInstallerId(installer.getId());
        transfer.setInstallerCode(installer.getInstallerCode());
        transfer.setStatus(TransferStatus.COMPLETED);
        transfer.setRemark(remark);
        transfer.setCreatedAt(new Date());
        transfer.setUpdatedAt(new Date());

        try {
            transfer.setDeviceIds(objectMapper.writeValueAsString(deviceIds));
        } catch (JsonProcessingException e) {
            log.error("序列化设备ID列表失败", e);
            transfer.setDeviceIds(deviceIds.toString());
        }

        transferRepository.insert(transfer);

        // 创建设备归属记录
        int newLevel = fromLevel + 1;
        for (String deviceId : deviceIds) {
            DeviceVendor deviceVendor = new DeviceVendor();
            deviceVendor.setDeviceId(deviceId);
            deviceVendor.setInstallerId(installer.getId());
            deviceVendor.setInstallerCode(installer.getInstallerCode());
            deviceVendor.setVendorId(toVendorId);
            deviceVendor.setVendorCode(toVendor.getVendorCode());
            deviceVendor.setParentVendorId(fromVendorId);
            deviceVendor.setCommissionRate(commissionRate);
            deviceVendor.setLevel(newLevel);
            deviceVendor.setTransferId(transfer.getId());
            deviceVendor.setCreatedAt(new Date());
            deviceVendor.setUpdatedAt(new Date());
            deviceVendorRepository.insert(deviceVendor);

            // 更新设备表的当前经销商
            ManufacturedDevice device = getDeviceByDeviceId(deviceId);
            if (device != null) {
                device.setCurrentVendorId(toVendorId);
                device.setUpdatedAt(new Date());
                deviceRepository.updateById(device);
            }
        }

        log.info("创建设备转让: transferNo={}, fromVendor={}, toVendor={}, count={}", 
                transferNo, fromVendorId, toVendorId, deviceIds.size());
        return transfer;
    }

    /**
     * 装机商直接分配设备给一级经销商
     */
    @Transactional
    public DeviceTransfer assignToVendor(Long installerId, Long vendorId, 
                                         List<String> deviceIds, BigDecimal commissionRate, String remark) {
        // 验证装机商
        Installer installer = installerRepository.selectById(installerId);
        if (installer == null) {
            throw new RuntimeException("装机商不存在");
        }

        // 验证经销商
        Vendor vendor = vendorRepository.selectById(vendorId);
        if (vendor == null) {
            throw new RuntimeException("经销商不存在");
        }

        // 验证经销商属于该装机商
        if (!installerId.equals(vendor.getInstallerId())) {
            throw new RuntimeException("经销商不属于该装机商");
        }

        // 使用通用转让方法（fromVendorId=null表示装机商分配）
        return createTransfer(null, vendorId, deviceIds, commissionRate, remark);
    }

    /**
     * 获取经销商的设备列表
     */
    public List<DeviceVendor> listDevicesByVendor(Long vendorId) {
        QueryWrapper<DeviceVendor> qw = new QueryWrapper<>();
        qw.lambda().eq(DeviceVendor::getVendorId, vendorId)
                .orderByDesc(DeviceVendor::getCreatedAt);
        return deviceVendorRepository.selectList(qw);
    }

    /**
     * 获取设备的完整分销链路
     */
    public List<DeviceVendor> getDeviceVendorChain(String deviceId) {
        return deviceVendorRepository.getVendorChainByDeviceId(deviceId);
    }

    /**
     * 分页查询转让记录
     */
    public Map<String, Object> listTransfers(Integer page, Integer size, Long vendorId, 
                                              Long installerId, String status) {
        QueryWrapper<DeviceTransfer> qw = new QueryWrapper<>();
        if (vendorId != null) {
            qw.lambda().and(w -> w.eq(DeviceTransfer::getFromVendorId, vendorId)
                    .or().eq(DeviceTransfer::getToVendorId, vendorId));
        }
        if (installerId != null) {
            qw.lambda().eq(DeviceTransfer::getInstallerId, installerId);
        }
        if (status != null && !status.trim().isEmpty()) {
            qw.lambda().eq(DeviceTransfer::getStatus, TransferStatus.fromCode(status));
        }
        qw.lambda().orderByDesc(DeviceTransfer::getCreatedAt);

        // 分页
        int offset = (page - 1) * size;
        qw.last("LIMIT " + offset + ", " + size);
        List<DeviceTransfer> list = transferRepository.selectList(qw);

        // 总数
        QueryWrapper<DeviceTransfer> countQw = new QueryWrapper<>();
        if (vendorId != null) {
            countQw.lambda().and(w -> w.eq(DeviceTransfer::getFromVendorId, vendorId)
                    .or().eq(DeviceTransfer::getToVendorId, vendorId));
        }
        if (installerId != null) {
            countQw.lambda().eq(DeviceTransfer::getInstallerId, installerId);
        }
        if (status != null && !status.trim().isEmpty()) {
            countQw.lambda().eq(DeviceTransfer::getStatus, TransferStatus.fromCode(status));
        }
        long total = transferRepository.selectCount(countQw);

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    /**
     * 获取转让详情
     */
    public DeviceTransfer getTransferById(Long id) {
        return transferRepository.selectById(id);
    }

    /**
     * 根据转让单号获取转让记录
     */
    public DeviceTransfer getByTransferNo(String transferNo) {
        QueryWrapper<DeviceTransfer> qw = new QueryWrapper<>();
        qw.lambda().eq(DeviceTransfer::getTransferNo, transferNo);
        return transferRepository.selectOne(qw);
    }

    /**
     * 生成转让单号
     */
    private String generateTransferNo() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String timestamp = sdf.format(new Date());
        String random = String.format("%04d", new Random().nextInt(10000));
        return "TF" + timestamp + random;
    }

    /**
     * 根据设备ID获取设备
     */
    private ManufacturedDevice getDeviceByDeviceId(String deviceId) {
        QueryWrapper<ManufacturedDevice> qw = new QueryWrapper<>();
        qw.lambda().eq(ManufacturedDevice::getDeviceId, deviceId);
        return deviceRepository.selectOne(qw);
    }
}
