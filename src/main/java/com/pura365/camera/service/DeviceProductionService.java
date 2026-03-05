package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.domain.*;
import com.pura365.camera.enums.EnableStatus;
import com.pura365.camera.enums.ManufacturedDeviceStatus;
import com.pura365.camera.enums.ProductionBatchStatus;
import com.pura365.camera.model.CreateBatchRequest;
import com.pura365.camera.domain.Salesman;
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
 * 设备生产管理服务
 * 负责设备ID生成、生产批次管理、经销商/装机商管理
 */
@Service
public class DeviceProductionService {

    private static final Logger log = LoggerFactory.getLogger(DeviceProductionService.class);

    // @Autowired
    // private VendorRepository vendorRepository; // 已废弃，改用 DealerRepository

    @Autowired
    private AssemblerRepository assemblerRepository;

    @Autowired
    private DeviceProductionBatchRepository batchRepository;

    @Autowired
    private ManufacturedDeviceRepository deviceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserDeviceRepository userDeviceRepository;

    @Autowired
    private SysDictService sysDictService;

    @Autowired
    private SalesmanRepository salesmanRepository;

    @Autowired
    private InstallerRepository installerRepository;

    @Autowired
    private DealerRepository dealerRepository;

    @Autowired
    private DeviceVendorRepository deviceVendorRepository;

    @Autowired
    private DeviceDealerRepository deviceDealerRepository;

    /**
     * 默认密码哈希(与admin2一致)
     */
    private static final String DEFAULT_PASSWORD_HASH = "$2a$10$Pw.m19MeKdWZvC.r5MrdiebsKTLcf6WS9fuWwmfAxX1IugFu2k.pS";

    // ==================== 经销商管理（已迁移到 DealerService） ====================
    // 以下旧的 Vendor 相关方法已废弃，请使用 DealerService

    // ==================== 装机商管理 ====================

    /**
     * 获取所有启用的装机商列表
     */
    public List<Assembler> listAssemblers() {
        QueryWrapper<Assembler> qw = new QueryWrapper<>();
        qw.lambda().eq(Assembler::getStatus, EnableStatus.ENABLED).orderByAsc(Assembler::getAssemblerCode);
        return assemblerRepository.selectList(qw);
    }

    // ==================== 配置选项 ====================

    /**
     * 获取启用的装机商列表（installer表，机身号第5位）
     */
    public List<Installer> listInstallers() {
        QueryWrapper<Installer> qw = new QueryWrapper<>();
        qw.lambda().eq(Installer::getStatus, EnableStatus.ENABLED).orderByAsc(Installer::getInstallerCode);
        return installerRepository.selectList(qw);
    }

    /**
     * 获取设备ID生成的所有配置选项
     * 包括：网络镜头配置、设备形态、特殊要求、装机商、经销商
     */
    public Map<String, Object> getOptions() {
        Map<String, Object> map = new HashMap<>();
        // 网络+镜头配置（机身号第1-2位）
        map.put("network_lens", getDictCodesOrDefault("network_lens",
                Arrays.asList("A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "R1")));
        map.put("network_lens_options", getDictOptionsOrDefault("network_lens",
                Arrays.asList("A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "R1")));
        // 设备形态（机身号第3位）
        map.put("device_form", getDictCodesOrDefault("device_form",
                Arrays.asList("1", "2", "3", "4", "5")));
        // 特殊要求（机身号第4位）
        map.put("special_req", getDictCodesOrDefault("special_req",
                Arrays.asList("0", "1", "2", "3")));
        // 预留位（机身号第8位）
        map.put("reserved", getDictCodesOrDefault("reserved", Collections.singletonList("0")));
        // 装机商列表（installer表，机身号第5位），转换为前端所需格式
        List<Map<String, Object>> installerList = new ArrayList<>();
        for (Installer installer : listInstallers()) {
            Map<String, Object> item = new HashMap<>();
            item.put("installerIdCode", installer.getInstallerCode());
            item.put("installerCode", installer.getInstallerCode());
            item.put("installerName", installer.getInstallerName());
            item.put("companyName", installer.getCompanyName());
            item.put("id", installer.getId());
            installerList.add(item);
        }
        map.put("installers", installerList);
        // 经销商列表（dealer表，机身号第6-7位），包含默认的00-先不指定选项
        List<Map<String, Object>> dealersWithDefault = new ArrayList<>();
        // 添加默认选项
        Map<String, Object> defaultDealer = new HashMap<>();
        defaultDealer.put("dealerCode", "00");
        defaultDealer.put("dealerIdCode", "00");
        defaultDealer.put("vendorCode", "00");
        defaultDealer.put("installerCode", "");
        defaultDealer.put("installerIdCode", "");
        defaultDealer.put("name", "先不指定");
        defaultDealer.put("companyName", null);
        dealersWithDefault.add(defaultDealer);
        // 添加实际经销商（从 dealer 表获取）
        for (Dealer d : listDealers()) {
            String dealerCode = d.getDealerCode() != null ? d.getDealerCode().trim() : "";
            if (dealerCode.isEmpty() || "00".equals(dealerCode)) {
                continue;
            }
            Map<String, Object> dealer = new HashMap<>();
            dealer.put("dealerCode", dealerCode);
            dealer.put("dealerIdCode", dealerCode);
            dealer.put("vendorCode", dealerCode);
            dealer.put("installerCode", d.getInstallerCode());
            dealer.put("installerIdCode", d.getInstallerCode());
            dealer.put("name", d.getName());
            dealer.put("companyName", d.getCompanyName());
            dealer.put("id", d.getId());
            dealersWithDefault.add(dealer);
        }
        map.put("dealers", dealersWithDefault);
        // 保留旧字段兼容
        map.put("assemblers", listAssemblers());
        return map;
    }

    private List<String> getDictCodesOrDefault(String category, List<String> defaults) {
        List<SysDict> dictItems = sysDictService.listByCategory(category);
        if (dictItems == null || dictItems.isEmpty()) {
            return new ArrayList<>(defaults);
        }
        Set<String> codes = new LinkedHashSet<>();
        for (SysDict item : dictItems) {
            if (item.getCode() != null && !item.getCode().trim().isEmpty()) {
                codes.add(item.getCode().trim());
            }
        }
        if (codes.isEmpty()) {
            return new ArrayList<>(defaults);
        }
        return new ArrayList<>(codes);
    }

    private List<Map<String, String>> getDictOptionsOrDefault(String category, List<String> defaults) {
        List<SysDict> dictItems = sysDictService.listByCategory(category);
        List<Map<String, String>> options = new ArrayList<>();
        if (dictItems != null && !dictItems.isEmpty()) {
            Set<String> seen = new LinkedHashSet<>();
            for (SysDict item : dictItems) {
                if (item.getCode() == null) {
                    continue;
                }
                String code = item.getCode().trim();
                if (code.isEmpty() || seen.contains(code)) {
                    continue;
                }
                Map<String, String> option = new HashMap<>();
                option.put("code", code);
                option.put("name", item.getName() != null && !item.getName().trim().isEmpty() ? item.getName().trim() : code);
                options.add(option);
                seen.add(code);
            }
            if (!options.isEmpty()) {
                return options;
            }
        }

        for (String code : defaults) {
            Map<String, String> option = new HashMap<>();
            option.put("code", code);
            option.put("name", code);
            options.add(option);
        }
        return options;
    }

    /**
     * 获取启用的经销商列表（dealer表，机身号第6-7位）
     */
    public List<Dealer> listDealers() {
        QueryWrapper<Dealer> qw = new QueryWrapper<>();
        qw.lambda().eq(Dealer::getStatus, EnableStatus.ENABLED).orderByAsc(Dealer::getDealerCode);
        return dealerRepository.selectList(qw);
    }


    // ==================== 业务员分配 ====================

    /**
     * 分配业务员到设备
     *
     * @param deviceId   设备ID
     * @param salesmanId 业务员ID
     */
    @Transactional
    public void assignSalesman(String deviceId, Long salesmanId) {
        ManufacturedDevice device = getDevice(deviceId);
        if (device == null) {
            throw new RuntimeException("设备不存在");
        }
        Salesman salesman = salesmanRepository.selectById(salesmanId);
        if (salesman == null) {
            throw new RuntimeException("业务员不存在");
        }
        // 校验业务员是否属于设备的经销商
        if (!salesman.getVendorCode().equals(device.getVendorCode())) {
            throw new RuntimeException("业务员不属于该设备的经销商");
        }
        device.setUpdatedAt(new Date());
        deviceRepository.updateById(device);
        log.info("分配业务员: deviceId={}, salesmanId={}", deviceId, salesmanId);
    }

    /**
     * 批量分配业务员到设备
     *
     * @param deviceIds  设备ID列表
     * @param salesmanId 业务员ID
     */
    @Transactional
    public void batchAssignSalesman(List<String> deviceIds, Long salesmanId) {
        Salesman salesman = salesmanRepository.selectById(salesmanId);
        if (salesman == null) {
            throw new RuntimeException("业务员不存在");
        }
        for (String deviceId : deviceIds) {
            ManufacturedDevice device = getDevice(deviceId);
            if (device != null && salesman.getVendorCode().equals(device.getVendorCode())) {
                device.setUpdatedAt(new Date());
                deviceRepository.updateById(device);
            }
        }
        log.info("批量分配业务员: deviceCount={}, salesmanId={}", deviceIds.size(), salesmanId);
    }

    /**
     * 移除设备的业务员分配
     *
     * @param deviceId 设备ID
     */
    @Transactional
    public void removeSalesmanAssignment(String deviceId) {
        ManufacturedDevice device = getDevice(deviceId);
        if (device == null) {
            throw new RuntimeException("设备不存在");
        }
        device.setUpdatedAt(new Date());
        deviceRepository.updateById(device);
        log.info("移除业务员分配: deviceId={}", deviceId);
    }

    // ==================== 扫码分配经销商 ====================

    /**
     * 扫码分配经销商
     * 更新设备的经销商关联，不修改设备ID
     *
     * @param deviceId   原设备ID（16位）
     * @param vendorCode 新的经销商代码（2位）
     * @return 包含新设备ID等信息的Map
     */
    @Transactional
    public Map<String, Object> scanAssignDealer(Long currentUserId, String deviceId, String vendorCode) {
        // 校验设备ID长度
        if (deviceId == null || deviceId.length() != 16) {
            throw new RuntimeException("设备ID必须是16位");
        }

        // 校验经销商代码
        if (vendorCode == null || vendorCode.length() != 2) {
            throw new RuntimeException("经销商代码必须是2位");
        }

        // 查找设备
        ManufacturedDevice device = getDevice(deviceId);
        if (device == null) {
            throw new RuntimeException("设备不存在: " + deviceId);
        }

        String allowedDealerCode = resolveAssignableDealerCode(currentUserId);
        String currentDealerCode = resolveCurrentDealerCode(device);
        if (currentDealerCode == null || !allowedDealerCode.equals(currentDealerCode)) {
            throw new RuntimeException("设备不属于当前经销商，无权限分配");
        }

        // 如果vendorCode不是"00"，校验经销商是否存在且启用
        Dealer targetDealer = null;
        if (!"00".equals(vendorCode)) {
            QueryWrapper<Dealer> dq = new QueryWrapper<>();
            dq.lambda().eq(Dealer::getDealerCode, vendorCode).eq(Dealer::getStatus, EnableStatus.ENABLED);
            targetDealer = dealerRepository.selectOne(dq);
            if (targetDealer == null) {
                throw new RuntimeException("经销商不存在或未启用: " + vendorCode);
            }
        }

        ensureDeviceNotBound(device.getDeviceId());

        String oldDeviceId = device.getDeviceId();
        String oldVendorCode = resolveInitialDealerCode(device);
        BigDecimal previousDealerRate = device.getDealerCommissionRate();
        boolean vendorChanged = !vendorCode.equals(currentDealerCode);

        // 经销商代码变化时，仅更新归属，不修改设备ID
        if (vendorChanged) {
            // 补齐历史设备的初始链路节点，避免链路只有下级节点
            ensureCurrentDealerInChain(oldDeviceId, device, oldVendorCode, previousDealerRate);
        }

        // 更新设备信息（机身码保持不变）
        if (oldVendorCode != null) {
            device.setVendorCode(oldVendorCode);
        }
        device.setCurrentDealerId(targetDealer != null ? targetDealer.getId() : null);
        device.setUpdatedAt(new Date());
        deviceRepository.updateById(device);

        if (vendorChanged && targetDealer != null) {
            BigDecimal chainRate = previousDealerRate != null ? previousDealerRate : BigDecimal.ZERO;
            try {
                writeDeviceDealerRecord(oldDeviceId, device, targetDealer, chainRate);
            } catch (Exception e) {
                log.warn("扫码分配补写 device_dealer 失败: deviceId={}, dealerCode={}, error={}",
                        oldDeviceId, vendorCode, e.getMessage());
            }
        }

        log.info("扫码分配经销商成功: deviceId={}, vendorCode={}, changed={}",
                oldDeviceId, vendorCode, vendorChanged);

        Map<String, Object> result = new HashMap<>();
        result.put("deviceId", oldDeviceId);
        result.put("newDeviceId", oldDeviceId);
        result.put("vendorCode", oldVendorCode);
        result.put("currentDealerCode", vendorCode);
        result.put("changed", vendorChanged);
        result.put("message", vendorChanged ? "分配成功" : "经销商未变化");
        return result;
    }

    /**
     * 批量分配经销商到设备（扫码分配）
     * 同时设置设备的经销商佣金比例
     * 只能分配当前用户设备管理列表中的设备
     *
     * @param currentUserId  当前登录用户ID
     * @param deviceIds      设备ID列表
     * @param dealerCode     经销商代码（2位）
     * @param commissionRate 佣金比例
     * @return 处理结果
     */
    public Map<String, Object> checkScanAssignDealer(Long currentUserId, String deviceId, String vendorCode) {
        if (deviceId == null || deviceId.length() != 16) {
            throw new RuntimeException("deviceId must be 16 chars");
        }
        if (vendorCode == null || vendorCode.length() != 2) {
            throw new RuntimeException("vendorCode must be 2 chars");
        }

        ManufacturedDevice device = getDevice(deviceId);
        if (device == null) {
            throw new RuntimeException("device not found: " + deviceId);
        }

        String allowedDealerCode = resolveAssignableDealerCode(currentUserId);
        String currentDealerCode = resolveCurrentDealerCode(device);
        if (currentDealerCode == null || !allowedDealerCode.equals(currentDealerCode)) {
            throw new RuntimeException("device is not owned by current dealer");
        }

        ensureDeviceNotBound(device.getDeviceId());

        if (!"00".equals(vendorCode)) {
            QueryWrapper<Dealer> dq = new QueryWrapper<>();
            dq.lambda().eq(Dealer::getDealerCode, vendorCode).eq(Dealer::getStatus, EnableStatus.ENABLED);
            if (dealerRepository.selectOne(dq) == null) {
                throw new RuntimeException("dealer not found or disabled: " + vendorCode);
            }
        }

        String initialDealerCode = resolveInitialDealerCode(device);
        Map<String, Object> result = new HashMap<>();
        result.put("deviceId", deviceId);
        result.put("vendorCode", initialDealerCode);
        result.put("currentDealerCode", currentDealerCode);
        result.put("targetDealerCode", vendorCode);
        result.put("canAssign", true);
        return result;
    }

    @Transactional
    public Map<String, Object> batchAssignDealer(Long currentUserId, List<String> deviceIds, String dealerCode, BigDecimal commissionRate) {
        // 校验经销商代码
        if (dealerCode == null || dealerCode.length() != 2) {
            throw new RuntimeException("经销商代码必须是2位");
        }

        // 仅经销商（含同时具备装机商+经销商身份）可分配经销商
        String allowedDealerCode = resolveAssignableDealerCode(currentUserId);

        // 查找经销商（非"00"时）
        Dealer dealer = null;
        if (!"00".equals(dealerCode)) {
            QueryWrapper<Dealer> dq = new QueryWrapper<>();
            dq.lambda().eq(Dealer::getDealerCode, dealerCode).eq(Dealer::getStatus, EnableStatus.ENABLED);
            dealer = dealerRepository.selectOne(dq);
            if (dealer == null) {
                throw new RuntimeException("经销商不存在或未启用: " + dealerCode);
            }
        }

        List<Map<String, Object>> results = new ArrayList<>();
        List<String> noPermissionDevices = new ArrayList<>(); // 无权限的设备列表
        List<String> boundDevices = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        // 用于权限校验的最终变量
        final String finalAllowedDealerCode = allowedDealerCode;

        for (String deviceId : deviceIds) {
            Map<String, Object> item = new HashMap<>();
            item.put("deviceId", deviceId);
            try {
                // 校验设备ID长度
                if (deviceId == null || deviceId.length() != 16) {
                    throw new RuntimeException("设备ID必须是16位");
                }

                // 查找设备
                ManufacturedDevice device = getDevice(deviceId);
                if (device == null) {
                    throw new RuntimeException("设备不存在");
                }

                // 权限校验：仅允许当前设备归属经销商分配给其他经销商
                String currentDealerCode = resolveCurrentDealerCode(device);
                if (currentDealerCode == null || !finalAllowedDealerCode.equals(currentDealerCode)) {
                    noPermissionDevices.add(deviceId);
                    throw new RuntimeException("设备不属于当前经销商，无权限分配");
                }

                if (isDeviceBound(device.getDeviceId())) {
                    boundDevices.add(deviceId);
                    throw new RuntimeException("设备已绑定用户，禁止分配经销商");
                }

                String oldDeviceId = device.getDeviceId();
                String oldVendorCode = resolveInitialDealerCode(device);
                BigDecimal previousDealerRate = device.getDealerCommissionRate();
                boolean vendorChanged = !dealerCode.equals(currentDealerCode);
                String newDeviceId = oldDeviceId;

                // 如果经销商代码变化，仅更新归属，不修改设备ID
                if (vendorChanged) {
                    // 补齐历史设备的初始链路节点，避免后续只出现下级节点导致链路断层
                    ensureCurrentDealerInChain(oldDeviceId, device, oldVendorCode, previousDealerRate);
                }
                if (oldVendorCode != null) {
                    device.setVendorCode(oldVendorCode);
                }

                BigDecimal effectiveDealerRate = commissionRate != null ? commissionRate : previousDealerRate;

                // 更新经销商ID和佣金比例
                if (dealer != null) {
                    device.setCurrentDealerId(dealer.getId());
                } else {
                    device.setCurrentDealerId(null);
                }
                device.setUpdatedAt(new Date());
                deviceRepository.updateById(device);

                // 写入 device_dealer 表记录分销链路
                if (dealer != null) {
                    BigDecimal chainRate = effectiveDealerRate != null ? effectiveDealerRate : BigDecimal.ZERO;
                    try {
                        writeDeviceDealerRecord(oldDeviceId, device, dealer, chainRate);
                    } catch (Exception e) {
                        log.warn("写入 device_dealer 失败: {}", e.getMessage());
                    }
                }

                item.put("newDeviceId", newDeviceId);
                item.put("success", true);
                item.put("changed", vendorChanged);
                successCount++;
            } catch (Exception e) {
                item.put("success", false);
                item.put("error", e.getMessage());
                failCount++;
            }
            results.add(item);
        }

        log.info("批量分配经销商完成: dealerCode={}, total={}, success={}, fail={}",
                dealerCode, deviceIds.size(), successCount, failCount);

        Map<String, Object> response = new HashMap<>();
        response.put("results", results);
        response.put("total", deviceIds.size());
        response.put("successCount", successCount);
        response.put("failCount", failCount);
        response.put("noPermissionDevices", noPermissionDevices); // 无权限的设备ID列表
        response.put("noPermissionCount", noPermissionDevices.size()); // 无权限设备数量
        response.put("boundDevices", boundDevices);
        response.put("boundCount", boundDevices.size());
        return response;
    }

    /**
     * Reject dealer assignment for devices already bound to user accounts.
     */
    private void ensureDeviceNotBound(String deviceId) {
        if (isDeviceBound(deviceId)) {
            throw new RuntimeException("设备已绑定用户，禁止分配经销商");
        }
    }

    private String resolveAssignableDealerCode(Long currentUserId) {
        if (currentUserId == null) {
            throw new RuntimeException("仅经销商账号可分配经销商");
        }
        User currentUser = userRepository.selectById(currentUserId);
        if (currentUser == null) {
            throw new RuntimeException("当前用户不存在");
        }
        if (currentUser.getIsDealer() == null || currentUser.getIsDealer() != 1 || currentUser.getDealerId() == null) {
            throw new RuntimeException("仅经销商账号可分配经销商");
        }

        Dealer userDealer = dealerRepository.selectById(currentUser.getDealerId());
        String dealerCode = userDealer != null ? userDealer.getDealerCode() : null;
        if (dealerCode == null || dealerCode.trim().isEmpty()) {
            throw new RuntimeException("当前账号未绑定有效经销商");
        }
        return dealerCode;
    }

    private String resolveInitialDealerCode(ManufacturedDevice device) {
        if (device == null) {
            return null;
        }

        String deviceId = device.getDeviceId();
        if (deviceId != null && deviceId.length() >= 7) {
            String codeFromDeviceId = normalizeCode(deviceId.substring(5, 7), false);
            if (codeFromDeviceId != null) {
                return codeFromDeviceId;
            }
        }
        return normalizeCode(device.getVendorCode(), false);
    }

    private String resolveCurrentDealerCode(ManufacturedDevice device) {
        if (device == null) {
            return null;
        }

        if (device.getCurrentDealerId() != null) {
            Dealer currentDealer = dealerRepository.selectById(device.getCurrentDealerId());
            String currentDealerCode = currentDealer != null ? normalizeCode(currentDealer.getDealerCode(), false) : null;
            if (currentDealerCode != null) {
                return currentDealerCode;
            }
        }
        return resolveInitialDealerCode(device);
    }

    private void ensureCurrentDealerInChain(String deviceId, ManufacturedDevice device, String dealerCode, BigDecimal commissionRate) {
        if (deviceId == null || dealerCode == null || dealerCode.trim().isEmpty() || "00".equals(dealerCode)) {
            return;
        }
        try {
            List<DeviceDealer> existingChain = deviceDealerRepository.getDealerChainByDeviceId(deviceId);
            if (existingChain != null && !existingChain.isEmpty()) {
                return;
            }

            QueryWrapper<Dealer> dealerQw = new QueryWrapper<>();
            dealerQw.lambda().eq(Dealer::getDealerCode, dealerCode);
            Dealer dealer = dealerRepository.selectOne(dealerQw);
            if (dealer == null) {
                return;
            }

            BigDecimal seedRate = commissionRate != null ? commissionRate : BigDecimal.ZERO;
            writeDeviceDealerRecord(deviceId, device, dealer, seedRate);
        } catch (Exception e) {
            // 历史数据兼容：失败不阻断主流程
            log.warn("补写初始 device_dealer 链路失败: deviceId={}, dealerCode={}, error={}",
                    deviceId, dealerCode, e.getMessage());
        }
    }

    private boolean isDeviceBound(String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return false;
        }
        QueryWrapper<UserDevice> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(UserDevice::getDeviceId, deviceId.trim());
        Long count = userDeviceRepository.selectCount(queryWrapper);
        return count != null && count > 0;
    }

    /**
     * 写入设备经销商归属记录（分销链路）
     * 如果设备已有经销商记录，则新记录的 parentDealerId 指向前一个经销商
     */
    private void writeDeviceDealerRecord(String deviceId, ManufacturedDevice device, Dealer dealer, BigDecimal commissionRate) {
        // 查询该设备当前的最高层级经销商记录
        List<DeviceDealer> existingChain = deviceDealerRepository.getDealerChainByDeviceId(deviceId);
        
        // 检查是否已存在相同的经销商记录
        for (DeviceDealer dd : existingChain) {
            if (dealer.getId().equals(dd.getDealerId())) {
                // 已存在，更新分润比例
                dd.setCommissionRate(commissionRate);
                dd.setUpdatedAt(new Date());
                deviceDealerRepository.updateById(dd);
                log.info("更新 device_dealer 记录: deviceId={}, dealerId={}, rate={}", 
                        deviceId, dealer.getId(), commissionRate);
                return;
            }
        }
        
        // 确定层级和上级经销商
        int newLevel = 1;
        Long parentDealerId = null;
        if (!existingChain.isEmpty()) {
            DeviceDealer lastDealer = existingChain.get(existingChain.size() - 1);
            newLevel = lastDealer.getLevel() + 1;
            parentDealerId = lastDealer.getDealerId();
        }
        
        // 获取装机商信息
        Long installerId = device.getInstallerId();
        String installerCode = device.getAssemblerCode();
        if (installerId == null && installerCode != null) {
            // 尝试根据 installerCode 查找
            QueryWrapper<Installer> iq = new QueryWrapper<>();
            iq.lambda().eq(Installer::getInstallerCode, installerCode);
            Installer installer = installerRepository.selectOne(iq);
            if (installer != null) {
                installerId = installer.getId();
            }
        }
        
        // 创建新的归属记录
        DeviceDealer deviceDealer = new DeviceDealer();
        deviceDealer.setDeviceId(deviceId);
        deviceDealer.setInstallerId(installerId);
        deviceDealer.setInstallerCode(installerCode);
        deviceDealer.setDealerId(dealer.getId());
        deviceDealer.setDealerCode(dealer.getDealerCode());
        deviceDealer.setParentDealerId(parentDealerId);
        deviceDealer.setCommissionRate(commissionRate);
        deviceDealer.setLevel(newLevel);
        deviceDealer.setCreatedAt(new Date());
        deviceDealer.setUpdatedAt(new Date());
        
        deviceDealerRepository.insert(deviceDealer);
        log.info("插入 device_dealer 记录: deviceId={}, dealerId={}, level={}, parentDealerId={}, rate={}", 
                deviceId, dealer.getId(), newLevel, parentDealerId, commissionRate);
    }

    private void syncDeviceDealerDeviceId(String oldDeviceId, String newDeviceId) {
        if (oldDeviceId == null || newDeviceId == null || oldDeviceId.equals(newDeviceId)) {
            return;
        }
        try {
            int affected = deviceDealerRepository.updateDeviceId(oldDeviceId, newDeviceId);
            if (affected > 0) {
                log.info("同步 device_dealer 设备ID: oldDeviceId={}, newDeviceId={}, affected={}",
                        oldDeviceId, newDeviceId, affected);
            }
        } catch (Exception e) {
            // device_dealer 表可能不存在或数据尚未迁移，不阻断主流程
            log.warn("同步 device_dealer 设备ID失败: oldDeviceId={}, newDeviceId={}, error={}",
                    oldDeviceId, newDeviceId, e.getMessage());
        }
    }

    private static class DeviceDataScope {
        private final boolean admin;
        private final boolean installerRoleScope;
        private final boolean dealerRoleScope;
        private final boolean dealerPermissionByChain;
        private final String effectiveInstallerCode;
        private final String effectiveDealerCode;
        private final Long userDealerId;
        private final Set<String> dealerManagedDeviceIds;

        private DeviceDataScope(
                boolean admin,
                boolean installerRoleScope,
                boolean dealerRoleScope,
                boolean dealerPermissionByChain,
                String effectiveInstallerCode,
                String effectiveDealerCode,
                Long userDealerId,
                Set<String> dealerManagedDeviceIds
        ) {
            this.admin = admin;
            this.installerRoleScope = installerRoleScope;
            this.dealerRoleScope = dealerRoleScope;
            this.dealerPermissionByChain = dealerPermissionByChain;
            this.effectiveInstallerCode = effectiveInstallerCode;
            this.effectiveDealerCode = effectiveDealerCode;
            this.userDealerId = userDealerId;
            this.dealerManagedDeviceIds = dealerManagedDeviceIds == null ? Collections.emptySet() : dealerManagedDeviceIds;
        }

        private boolean hasRoleScope() {
            return admin || installerRoleScope || dealerRoleScope;
        }
    }

    private DeviceDataScope resolveDeviceDataScope(Long currentUserId, String installerCode, String dealerCode) {
        String effectiveInstallerCode = installerCode;
        String effectiveDealerCode = dealerCode;
        boolean hasInstallerRoleScope = false;
        boolean hasDealerRoleScope = false;
        boolean dealerPermissionByChain = false;
        boolean isAdmin = false;
        Long userDealerId = null;
        Set<String> dealerManagedDeviceIds = Collections.emptySet();

        if (currentUserId != null) {
            User currentUser = userRepository.selectById(currentUserId);
            if (currentUser != null) {
                isAdmin = currentUser.getRole() != null && currentUser.getRole() == 3;

                if (!isAdmin) {
                    if (currentUser.getIsInstaller() != null && currentUser.getIsInstaller() == 1 && currentUser.getInstallerId() != null) {
                        Installer installer = installerRepository.selectById(currentUser.getInstallerId());
                        if (installer != null && installer.getInstallerCode() != null) {
                            effectiveInstallerCode = installer.getInstallerCode();
                            hasInstallerRoleScope = true;
                            log.info("装机商用户查询设备数据范围, userId={}, installerCode={}", currentUserId, effectiveInstallerCode);
                        }
                    }

                    if (currentUser.getIsDealer() != null && currentUser.getIsDealer() == 1 && currentUser.getDealerId() != null) {
                        Dealer dealer = dealerRepository.selectById(currentUser.getDealerId());
                        if (dealer != null && dealer.getDealerCode() != null) {
                            effectiveDealerCode = dealer.getDealerCode();
                            userDealerId = dealer.getId();
                            hasDealerRoleScope = true;
                            dealerPermissionByChain = true;
                            log.info("经销商用户查询设备数据范围, userId={}, dealerCode={}", currentUserId, effectiveDealerCode);
                        }
                    }
                }
            }
        }

        if (dealerPermissionByChain && effectiveDealerCode != null && !effectiveDealerCode.trim().isEmpty()) {
            dealerManagedDeviceIds = listDealerManagedDeviceIds(effectiveDealerCode);
        }

        return new DeviceDataScope(
                isAdmin,
                hasInstallerRoleScope,
                hasDealerRoleScope,
                dealerPermissionByChain,
                effectiveInstallerCode,
                effectiveDealerCode,
                userDealerId,
                dealerManagedDeviceIds
        );
    }

    private boolean canAccessDevice(ManufacturedDevice device, DeviceDataScope scope) {
        if (device == null || scope == null) {
            return false;
        }
        if (scope.admin) {
            return true;
        }
        if (!scope.hasRoleScope()) {
            return false;
        }
        if (scope.installerRoleScope && scope.effectiveInstallerCode != null
                && scope.effectiveInstallerCode.equals(device.getAssemblerCode())) {
            return true;
        }
        if (scope.dealerRoleScope && scope.effectiveDealerCode != null) {
            if (scope.effectiveDealerCode.equals(device.getVendorCode())) {
                return true;
            }
            return scope.dealerManagedDeviceIds.contains(device.getDeviceId());
        }
        return false;
    }

    private Map<String, Object> emptyPageResult(Integer page, Integer size) {
        Map<String, Object> result = new HashMap<>();
        result.put("list", new ArrayList<>());
        result.put("total", 0);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    private Set<String> listDealerManagedDeviceIds(String dealerCode) {
        if (dealerCode == null || dealerCode.trim().isEmpty()) {
            return Collections.emptySet();
        }
        try {
            List<String> deviceIds = deviceDealerRepository.listDeviceIdsByDealerCode(dealerCode);
            if (deviceIds == null || deviceIds.isEmpty()) {
                return Collections.emptySet();
            }
            Set<String> result = new HashSet<>();
            for (String id : deviceIds) {
                if (id != null && !id.trim().isEmpty()) {
                    result.add(id);
                }
            }
            return result;
        } catch (Exception e) {
            // device_dealer 表可能不存在或数据尚未迁移，回退到仅 vendor_code 过滤
            log.warn("查询经销商管理设备链路失败: dealerCode={}, error={}", dealerCode, e.getMessage());
            return Collections.emptySet();
        }
    }

    private void applyDealerFilter(QueryWrapper<ManufacturedDevice> qw, String dealerCode, Set<String> dealerManagedDeviceIds) {
        if (dealerCode == null || dealerCode.trim().isEmpty()) {
            return;
        }
        if (dealerManagedDeviceIds == null || dealerManagedDeviceIds.isEmpty()) {
            qw.lambda().eq(ManufacturedDevice::getVendorCode, dealerCode);
            return;
        }
        qw.lambda().and(w -> w.eq(ManufacturedDevice::getVendorCode, dealerCode)
                .or().in(ManufacturedDevice::getDeviceId, dealerManagedDeviceIds));
    }

    private void applyRoleDeviceFilter(
            QueryWrapper<ManufacturedDevice> qw,
            String installerCode,
            String dealerCode,
            Set<String> dealerManagedDeviceIds,
            boolean useRoleOrScope
    ) {
        String installer = installerCode == null ? null : installerCode.trim();
        String dealer = dealerCode == null ? null : dealerCode.trim();
        boolean hasInstaller = installer != null && !installer.isEmpty();
        boolean hasDealer = dealer != null && !dealer.isEmpty();

        if (!useRoleOrScope) {
            if (hasInstaller) {
                qw.lambda().eq(ManufacturedDevice::getAssemblerCode, installer);
            }
            if (hasDealer) {
                applyDealerFilter(qw, dealer, dealerManagedDeviceIds);
            }
            return;
        }

        if (!hasInstaller && !hasDealer) {
            return;
        }
        if (!hasInstaller) {
            applyDealerFilter(qw, dealer, dealerManagedDeviceIds);
            return;
        }
        if (!hasDealer) {
            qw.lambda().eq(ManufacturedDevice::getAssemblerCode, installer);
            return;
        }

        if (dealerManagedDeviceIds == null || dealerManagedDeviceIds.isEmpty()) {
            qw.lambda().and(w -> w.eq(ManufacturedDevice::getAssemblerCode, installer)
                    .or().eq(ManufacturedDevice::getVendorCode, dealer));
            return;
        }
        qw.lambda().and(w -> w.eq(ManufacturedDevice::getAssemblerCode, installer)
                .or().eq(ManufacturedDevice::getVendorCode, dealer)
                .or().in(ManufacturedDevice::getDeviceId, dealerManagedDeviceIds));
    }

    // ==================== 生产批次管理 ====================

    /**
     * 创建生产批次并批量生成设备ID入库
     *
     * @param request 创建批次请求参数
     * @return 创建的批次信息
     */
    @Transactional
    public DeviceProductionBatch createBatch(CreateBatchRequest request) {
        // 参数校验
        validateBatchRequest(request);

        String networkLens = request.getNetworkLens();
        String deviceForm = request.getDeviceForm();
        String specialReq = request.getSpecialReq();
        String assemblerCode = request.getAssemblerCode();
        String vendorCode = request.getVendorCode();
        String reserved = request.getReserved() != null ? request.getReserved() : "0";
        String networkLensDesc = request.getNetworkLensDesc();
        int quantity = request.getQuantity();

        ensureNetworkLensDictItem(networkLens, networkLensDesc);

        // 校验经销商是否存在且启用（00表示先不指定，跳过校验）
        if (!"00".equals(vendorCode)) {
            QueryWrapper<Dealer> dq = new QueryWrapper<>();
            dq.lambda().eq(Dealer::getDealerCode, vendorCode).eq(Dealer::getStatus, EnableStatus.ENABLED);
            if (dealerRepository.selectCount(dq) == 0) {
                throw new RuntimeException("经销商不存在或未启用");
            }
        }

        // 校验装机商是否存在且启用
//        QueryWrapper<Assembler> aq = new QueryWrapper<>();
//        aq.lambda().eq(Assembler::getAssemblerCode, assemblerCode).eq(Assembler::getStatus, 1);
//        if (assemblerRepository.selectCount(aq) == 0) {
//            throw new RuntimeException("装机商不存在或未启用");
//        }

        // 生成批次号 (PByyyyMMddNNN)
        String batchNo = generateBatchNo();

        // 计算起始序列号
        int startSerial = request.getStartSerial() != null
                ? request.getStartSerial()
                : calculateNextStartSerial(networkLens, deviceForm, specialReq, assemblerCode, vendorCode, reserved);
        int endSerial = startSerial + quantity - 1;

        // 创建批次记录
        DeviceProductionBatch batch = new DeviceProductionBatch();
        batch.setBatchNo(batchNo);
        batch.setNetworkLens(networkLens);
        batch.setDeviceForm(deviceForm);
        batch.setSpecialReq(specialReq);
        batch.setAssemblerCode(assemblerCode);
        batch.setVendorCode(vendorCode);
        batch.setReserved(reserved);
        batch.setQuantity(quantity);
        batch.setStartSerial(startSerial);
        batch.setEndSerial(endSerial);
        batch.setStatus(ProductionBatchStatus.COMPLETED);
        batch.setRemark(request.getRemark());
        batch.setCreatedBy(request.getCreatedBy());
        batch.setInstallerCommissionRate(request.getInstallerCommissionRate());
        batch.setDealerCommissionRate(request.getDealerCommissionRate());
        batch.setEnableAd(request.getEnableAd() != null ? request.getEnableAd() : true);
        batchRepository.insert(batch);

        // 批量生成设备ID
        String prefix = batch.getDeviceIdPrefix();
        generateDevices(batch, prefix, networkLens, deviceForm, specialReq,
                assemblerCode, vendorCode, startSerial, endSerial);

        log.info("创建生产批次成功: batchNo={}, quantity={}, startSerial={}, endSerial={}",
                batchNo, quantity, startSerial, endSerial);
        return batch;
    }

    /**
     * 校验创建批次请求参数
     */
    private void validateBatchRequest(CreateBatchRequest request) {
        if (request == null) {
            throw new RuntimeException("Request cannot be null");
        }

        request.setNetworkLens(normalizeCode(request.getNetworkLens(), true));
        request.setDeviceForm(normalizeCode(request.getDeviceForm(), false));
        request.setSpecialReq(normalizeCode(request.getSpecialReq(), false));
        request.setAssemblerCode(normalizeCode(request.getAssemblerCode(), false));
        request.setVendorCode(normalizeCode(request.getVendorCode(), false));
        String normalizedReserved = normalizeCode(request.getReserved(), false);
        request.setReserved(normalizedReserved != null ? normalizedReserved : "0");
        request.setNetworkLensDesc(normalizeText(request.getNetworkLensDesc()));
        if (request.getNetworkLens() == null || request.getNetworkLens().length() != 2) {
            throw new RuntimeException("网络+镜头配置(第1-2位)必须是2位");
        }
        if (request.getDeviceForm() == null || request.getDeviceForm().length() != 1) {
            throw new RuntimeException("设备形态(第3位)必须是1位");
        }
        if (request.getSpecialReq() == null || request.getSpecialReq().length() != 1) {
            throw new RuntimeException("特殊要求(第4位)必须是1位");
        }
        if (request.getAssemblerCode() == null || request.getAssemblerCode().length() != 1) {
            throw new RuntimeException("装机商代码(第5位)必须是1位");
        }
        if (request.getVendorCode() == null || request.getVendorCode().length() != 2) {
            throw new RuntimeException("经销商代码(第6-7位)必须是2位");
        }
        if (request.getReserved() != null && request.getReserved().length() != 1) {
            throw new RuntimeException("预留位(第8位)必须是1位");
        }
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new RuntimeException("生产数量必须大于0");
        }
    }

    /**
     * 规范化代码：去空格，必要时转大写
     */
    private String normalizeCode(String value, boolean uppercase) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return uppercase ? normalized.toUpperCase(Locale.ROOT) : normalized;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void ensureNetworkLensDictItem(String networkLens, String networkLensDesc) {
        if (networkLens == null || networkLens.trim().isEmpty()) {
            return;
        }
        String inputName = normalizeText(networkLensDesc);
        String createName = inputName != null ? inputName : networkLens;

        SysDict existing = sysDictService.getByCategoryAndCode("network_lens", networkLens);
        if (existing == null) {
            try {
                SysDict dict = new SysDict();
                dict.setCategory("network_lens");
                dict.setCode(networkLens);
                dict.setName(createName);
                dict.setStatus(EnableStatus.ENABLED);
                sysDictService.create(dict);
                return;
            } catch (RuntimeException ex) {
                // 骞跺彂鍒涘缓鍚屼竴涓満鍨嬩唬鐮佹椂锛岃嫢宸叉湁璁板綍鍒欑户缁悗缁祦绋?
                existing = sysDictService.getByCategoryAndCode("network_lens", networkLens);
                if (existing == null) {
                    throw ex;
                }
            }
        }

        if (!EnableStatus.ENABLED.equals(existing.getStatus())) {
            sysDictService.updateStatus(existing.getId(), EnableStatus.ENABLED.getCode());
        }

        String existingName = normalizeText(existing.getName());
        if (existingName == null || (inputName != null && !inputName.equals(existingName))) {
            SysDict update = new SysDict();
            update.setName(inputName != null ? inputName : networkLens);
            sysDictService.update(existing.getId(), update);
        }
    }

    /**
     * 批量生成设备记录
     * 后8位使用随机字符（大小写字母+数字），确保唯一性
     */
    private void generateDevices(DeviceProductionBatch batch, String prefix, String networkLens, String deviceForm,
                                 String specialReq, String assemblerCode, String vendorCode,
                                 int startSerial, int endSerial) {
        int quantity = endSerial - startSerial + 1;
        Set<String> generatedSerials = new HashSet<>();
        
        // 根据 assemblerCode 查找装机商ID
        Long installerId = null;
        if (assemblerCode != null) {
            QueryWrapper<Installer> iq = new QueryWrapper<>();
            iq.lambda().eq(Installer::getInstallerCode, assemblerCode);
            Installer installer = installerRepository.selectOne(iq);
            if (installer != null) {
                installerId = installer.getId();
            }
        }
        
        // 根据 vendorCode 查找经销商ID
        Long currentDealerId = null;
        Dealer currentDealer = null;
        if (vendorCode != null) {
            QueryWrapper<Dealer> dq = new QueryWrapper<>();
            dq.lambda().eq(Dealer::getDealerCode, vendorCode);
            currentDealer = dealerRepository.selectOne(dq);
            if (currentDealer != null) {
                currentDealerId = currentDealer.getId();
            }
        }
        
        for (int i = 0; i < quantity; i++) {
            String serial8 = generateUniqueSerial8(prefix, generatedSerials);
            generatedSerials.add(serial8);
            String deviceId = prefix + serial8;

            ManufacturedDevice device = new ManufacturedDevice();
            device.setBatchId(batch.getId());
            device.setDeviceId(deviceId);
            device.setNetworkLens(networkLens);
            device.setDeviceForm(deviceForm);
            device.setSpecialReq(specialReq);
            device.setAssemblerCode(assemblerCode);
            device.setVendorCode(vendorCode);
            device.setInstallerId(installerId);
            device.setCurrentDealerId(currentDealerId);
            device.setSerialNo(serial8);
            device.setStatus(ManufacturedDeviceStatus.MANUFACTURED);
            // 从批次复制分润比例和广告开关
            device.setInstallerCommissionRate(batch.getInstallerCommissionRate());
            device.setDealerCommissionRate(batch.getDealerCommissionRate());
            device.setEnableAd(batch.getEnableAd());
            deviceRepository.insert(device);
        }
    }

    /**
     * 生成唯一的8位随机序列号（大小写字母+数字）
     * 确保在数据库和当前批次中都不重复
     */
    private String generateUniqueSerial8(String prefix, Set<String> currentBatchSerials) {
        for (int attempt = 0; attempt < 100; attempt++) {
            String serial8 = generateRandomString(8);
            
            // 检查当前批次是否已存在
            if (currentBatchSerials.contains(serial8)) {
                continue;
            }
            
            // 检查数据库是否已存在该设备ID
            String deviceId = prefix + serial8;
            QueryWrapper<ManufacturedDevice> qw = new QueryWrapper<>();
            qw.lambda().eq(ManufacturedDevice::getDeviceId, deviceId);
            if (deviceRepository.selectCount(qw) == 0) {
                return serial8;
            }
        }
        throw new RuntimeException("无法生成唯一的设备序列号，请稍后重试");
    }

    /**
     * 根据批次号获取批次信息
     *
     * @param batchNo 批次号
     * @return 批次实体
     */
    public DeviceProductionBatch getBatchByNo(String batchNo) {
        QueryWrapper<DeviceProductionBatch> qw = new QueryWrapper<>();
        qw.lambda().eq(DeviceProductionBatch::getBatchNo, batchNo);
        return batchRepository.selectOne(qw);
    }

    /**
     * 获取批次下的所有设备列表
     *
     * @param batchNo 批次号
     * @return 设备列表
     */
    public List<ManufacturedDevice> listDevicesByBatch(String batchNo) {
        DeviceProductionBatch batch = getBatchByNo(batchNo);
        if (batch == null) {
            return Collections.emptyList();
        }
        QueryWrapper<ManufacturedDevice> qw = new QueryWrapper<>();
        qw.lambda().eq(ManufacturedDevice::getBatchId, batch.getId())
                .orderByAsc(ManufacturedDevice::getSerialNo);
        return deviceRepository.selectList(qw);
    }

    // ==================== 设备查询 ====================

    /**
     * 根据设备ID获取设备信息
     *
     * @param deviceId 设备ID (16位)
     * @return 设备实体
     */
    public ManufacturedDevice getDevice(String deviceId) {
        QueryWrapper<ManufacturedDevice> qw = new QueryWrapper<>();
        qw.lambda().eq(ManufacturedDevice::getDeviceId, deviceId);
        return deviceRepository.selectOne(qw);
    }

    /**
     * 获取设备详情（包含分销链路等附加信息）
     *
     * @param deviceId 设备ID (16位)
     * @return 包含详情的Map，如果设备不存在返回null
     */
    public Map<String, Object> getDeviceDetail(Long currentUserId, String deviceId) {
        ManufacturedDevice device = getDevice(deviceId);
        if (device == null) {
            return null;
        }

        DeviceDataScope scope = resolveDeviceDataScope(currentUserId, null, null);
        if (!canAccessDevice(device, scope)) {
            log.warn("无权限查看设备详情, userId={}, deviceId={}", currentUserId, deviceId);
            return null;
        }

        // 复用 enrichDevicesWithCommission 方法获取完整信息
        List<Map<String, Object>> enrichedList = enrichDevicesWithCommission(Collections.singletonList(device), scope);
        return enrichedList.isEmpty() ? null : enrichedList.get(0);
    }

    /**
     * 分页查询设备列表
     * 根据当前登录用户的角色自动过滤：
     * - 管理员(role=3)：可查看所有设备
     * - 装机商(isInstaller=1)：只能查看自己关联的设备
     * - 经销商(isDealer=1)：只能查看自己关联的设备
     */
    public Map<String, Object> listDevices(Long currentUserId, Integer page, Integer size, String deviceId, String batchNo, String status, String installerCode, String dealerCode) {
        DeviceDataScope scope = resolveDeviceDataScope(currentUserId, installerCode, dealerCode);
        if (!scope.hasRoleScope()) {
            return emptyPageResult(page, size);
        }

        QueryWrapper<ManufacturedDevice> qw = new QueryWrapper<>();
        if (deviceId != null && !deviceId.trim().isEmpty()) {
            qw.lambda().like(ManufacturedDevice::getDeviceId, deviceId);
        }
        if (batchNo != null && !batchNo.trim().isEmpty()) {
            // 支持按批次ID(纯数字)或批次号(字符串)查询
            Long batchId = null;
            try {
                batchId = Long.parseLong(batchNo.trim());
            } catch (NumberFormatException ignored) {
            }

            if (batchId != null) {
                // 按批次ID查询
                qw.lambda().eq(ManufacturedDevice::getBatchId, batchId);
            } else {
                // 根据批次号查找批次ID
                QueryWrapper<DeviceProductionBatch> batchQw = new QueryWrapper<>();
                batchQw.lambda().eq(DeviceProductionBatch::getBatchNo, batchNo);
                DeviceProductionBatch batch = batchRepository.selectOne(batchQw);
                if (batch != null) {
                    qw.lambda().eq(ManufacturedDevice::getBatchId, batch.getId());
                } else {
                    return emptyPageResult(page, size);
                }
            }
        }
        if (status != null && !status.trim().isEmpty()) {
            ManufacturedDeviceStatus statusEnum = ManufacturedDeviceStatus.fromCode(status);
            if (statusEnum != null) {
                qw.lambda().eq(ManufacturedDevice::getStatus, statusEnum);
            }
        }
        Set<String> effectiveDealerScopeIds = scope.dealerPermissionByChain ? scope.dealerManagedDeviceIds : Collections.emptySet();
        boolean useRoleOrScope = scope.installerRoleScope && scope.dealerRoleScope;
        applyRoleDeviceFilter(
                qw,
                scope.effectiveInstallerCode,
                scope.effectiveDealerCode,
                effectiveDealerScopeIds,
                useRoleOrScope
        );
        qw.lambda().orderByDesc(ManufacturedDevice::getCreatedAt);

        // 分页查询
        int offset = (page - 1) * size;
        qw.last("LIMIT " + offset + ", " + size);
        List<ManufacturedDevice> devices = deviceRepository.selectList(qw);

        // 查询总数
        QueryWrapper<ManufacturedDevice> countQw = new QueryWrapper<>();
        if (deviceId != null && !deviceId.trim().isEmpty()) {
            countQw.lambda().like(ManufacturedDevice::getDeviceId, deviceId);
        }
        if (batchNo != null && !batchNo.trim().isEmpty()) {
            Long batchId = null;
            try {
                batchId = Long.parseLong(batchNo.trim());
            } catch (NumberFormatException ignored) {
            }

            if (batchId != null) {
                countQw.lambda().eq(ManufacturedDevice::getBatchId, batchId);
            } else {
                QueryWrapper<DeviceProductionBatch> batchQw = new QueryWrapper<>();
                batchQw.lambda().eq(DeviceProductionBatch::getBatchNo, batchNo);
                DeviceProductionBatch batch = batchRepository.selectOne(batchQw);
                if (batch != null) {
                    countQw.lambda().eq(ManufacturedDevice::getBatchId, batch.getId());
                }
            }
        }
        if (status != null && !status.trim().isEmpty()) {
            ManufacturedDeviceStatus statusEnum = ManufacturedDeviceStatus.fromCode(status);
            if (statusEnum != null) {
                countQw.lambda().eq(ManufacturedDevice::getStatus, statusEnum);
            }
        }
        applyRoleDeviceFilter(
                countQw,
                scope.effectiveInstallerCode,
                scope.effectiveDealerCode,
                effectiveDealerScopeIds,
                useRoleOrScope
        );
        long total = deviceRepository.selectCount(countQw);

        // 获取装机商和经销商的分佣比例
        List<Map<String, Object>> deviceList = enrichDevicesWithCommission(devices, scope);

        Map<String, Object> result = new HashMap<>();
        result.put("list", deviceList);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    private boolean canViewInstallerCommission(DeviceDataScope scope) {
        if (scope == null) {
            return true;
        }
        if (scope.admin) {
            return true;
        }
        if (scope.dealerRoleScope) {
            return false;
        }
        return scope.installerRoleScope;
    }

    private boolean canViewDealerCommission(DeviceDataScope scope, ManufacturedDevice device, List<DeviceDealer> dealerChain) {
        if (scope == null) {
            return true;
        }
        if (scope.admin) {
            return true;
        }
        if (!scope.dealerRoleScope || scope.userDealerId == null) {
            return false;
        }

        if (dealerChain != null && !dealerChain.isEmpty()) {
            for (DeviceDealer node : dealerChain) {
                if (scope.userDealerId.equals(node.getDealerId())) {
                    return true;
                }
            }
            return false;
        }

        // 兼容历史数据：device_dealer 未完整时，回退到 vendor_code 判断
        return scope.effectiveDealerCode != null && scope.effectiveDealerCode.equals(device.getVendorCode());
    }

    /**
     * 为设备列表添加装机商和经销商的分佣比例信息
     */
    private List<Map<String, Object>> enrichDevicesWithCommission(List<ManufacturedDevice> devices, DeviceDataScope scope) {
        if (devices == null || devices.isEmpty()) {
            return new ArrayList<>();
        }

        // 收集所有的 dealerCode、currentDealerId、installerId 和 batchId
        Set<String> dealerCodes = new HashSet<>();
        Set<Long> dealerIds = new HashSet<>();
        Set<Long> installerIds = new HashSet<>();
        Set<Long> batchIds = new HashSet<>();
        for (ManufacturedDevice d : devices) {
            if (d.getVendorCode() != null && !"00".equals(d.getVendorCode())) {
                dealerCodes.add(d.getVendorCode());
            }
            if (d.getCurrentDealerId() != null) {
                dealerIds.add(d.getCurrentDealerId());
            }
            if (d.getInstallerId() != null) {
                installerIds.add(d.getInstallerId());
            }
            if (d.getBatchId() != null) {
                batchIds.add(d.getBatchId());
            }
        }

        // 批量查询 Dealer（按代码和ID）
        Map<String, Dealer> dealerByCodeMap = new HashMap<>();
        Map<Long, Dealer> dealerByIdMap = new HashMap<>();
        if (!dealerCodes.isEmpty()) {
            QueryWrapper<Dealer> dq = new QueryWrapper<>();
            dq.lambda().in(Dealer::getDealerCode, dealerCodes);
            List<Dealer> dealers = dealerRepository.selectList(dq);
            for (Dealer d : dealers) {
                dealerByCodeMap.put(d.getDealerCode(), d);
                dealerByIdMap.put(d.getId(), d);
                // 同时收集经销商关联的装机商ID
                if (d.getInstallerId() != null) {
                    installerIds.add(d.getInstallerId());
                }
            }
        }
        // 按 ID 查询经销商
        if (!dealerIds.isEmpty()) {
            QueryWrapper<Dealer> dq = new QueryWrapper<>();
            dq.lambda().in(Dealer::getId, dealerIds);
            List<Dealer> dealers = dealerRepository.selectList(dq);
            for (Dealer d : dealers) {
                dealerByIdMap.put(d.getId(), d);
                if (d.getInstallerId() != null) {
                    installerIds.add(d.getInstallerId());
                }
            }
        }

        // 批量查询 Installer
        Map<Long, Installer> installerMap = new HashMap<>();
        if (!installerIds.isEmpty()) {
            QueryWrapper<Installer> iq = new QueryWrapper<>();
            iq.lambda().in(Installer::getId, installerIds);
            List<Installer> installers = installerRepository.selectList(iq);
            for (Installer i : installers) {
                installerMap.put(i.getId(), i);
            }
        }

        // 批量查询 DeviceProductionBatch 获取批次号
        Map<Long, String> batchNoMap = new HashMap<>();
        if (!batchIds.isEmpty()) {
            QueryWrapper<DeviceProductionBatch> bq = new QueryWrapper<>();
            bq.lambda().in(DeviceProductionBatch::getId, batchIds);
            List<DeviceProductionBatch> batches = batchRepository.selectList(bq);
            for (DeviceProductionBatch b : batches) {
                batchNoMap.put(b.getId(), b.getBatchNo());
            }
        }

        boolean showInstallerCommission = canViewInstallerCommission(scope);

        // 构建结果列表
        List<Map<String, Object>> resultList = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (ManufacturedDevice d : devices) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", d.getId());
            map.put("deviceId", d.getDeviceId());
            map.put("batchId", d.getBatchId());
            map.put("batchNo", d.getBatchId() != null ? batchNoMap.get(d.getBatchId()) : null);
            map.put("networkLens", d.getNetworkLens());
            map.put("deviceForm", d.getDeviceForm());
            map.put("specialReq", d.getSpecialReq());
            map.put("assemblerCode", d.getAssemblerCode());
            String initialDealerCode = resolveInitialDealerCode(d);
            map.put("vendorCode", initialDealerCode != null ? initialDealerCode : d.getVendorCode());
            map.put("installerId", d.getInstallerId());
            map.put("serialNo", d.getSerialNo());
            map.put("macAddress", d.getMacAddress());
            map.put("status", d.getStatus() != null ? d.getStatus().getCode() : null);
            map.put("country", d.getCountry());
            map.put("manufacturedAt", d.getManufacturedAt() != null ? sdf.format(d.getManufacturedAt()) : null);
            map.put("activatedAt", d.getActivatedAt() != null ? sdf.format(d.getActivatedAt()) : null);
            map.put("createdAt", d.getCreatedAt() != null ? sdf.format(d.getCreatedAt()) : null);
            map.put("updatedAt", d.getUpdatedAt() != null ? sdf.format(d.getUpdatedAt()) : null);
            map.put("enableAd", d.getEnableAd());

            // 添加装机商信息
            Installer installer = d.getInstallerId() != null ? installerMap.get(d.getInstallerId()) : null;
            if (installer != null) {
                map.put("installerName", installer.getInstallerName());
            } else {
                map.put("installerName", null);
            }
            map.put("canViewInstallerCommission", showInstallerCommission);
            map.put("installerCommissionRate", showInstallerCommission ? d.getInstallerCommissionRate() : null);

            // 添加经销商信息（优先根据 currentDealerId 查找）
            Dealer dealer = null;
            if (initialDealerCode != null) {
                dealer = dealerByCodeMap.get(initialDealerCode);
            }
            if (dealer == null && d.getCurrentDealerId() != null) {
                dealer = dealerByIdMap.get(d.getCurrentDealerId());
            }
            if (dealer != null) {
                map.put("dealerName", dealer.getName());
            } else {
                map.put("dealerName", null);
            }

            // 获取设备的分销链路（从 device_dealer 表）
            List<DeviceDealer> dealerChain = null;
            map.put("currentVendorId", null);
            map.put("currentVendorCode", null);
            map.put("currentVendorName", null);
            map.put("currentVendorLevel", null);
            map.put("currentVendorCommissionRate", null);
            map.put("vendorChain", null);
            map.put("vendorChainList", null);
            try {
                dealerChain = deviceDealerRepository.getDealerChainByDeviceId(d.getDeviceId());
            } catch (Exception e) {
                // device_dealer 表不存在或查询失败，跳过分销链路查询
                log.debug("查询设备分销链路失败: {}", e.getMessage());
            }

            boolean showDealerCommission = canViewDealerCommission(scope, d, dealerChain);
            map.put("canViewDealerCommission", showDealerCommission);
            map.put("dealerCommissionRate", showDealerCommission ? d.getDealerCommissionRate() : null);

            if (dealerChain != null && !dealerChain.isEmpty()) {
                // 当前持有经销商（最高层级）
                DeviceDealer currentDealer = dealerChain.get(dealerChain.size() - 1);
                map.put("currentVendorId", currentDealer.getDealerId());
                map.put("currentVendorCode", currentDealer.getDealerCode());
                map.put("currentVendorLevel", currentDealer.getLevel());
                map.put("currentVendorCommissionRate", showDealerCommission ? currentDealer.getCommissionRate() : null);

                // 构建分销链路字符串 (A → B → C)
                List<String> chainNames = new ArrayList<>();
                for (DeviceDealer dd : dealerChain) {
                    // 使用 dealer 表获取经销商名称
                    String name = "经销商" + dd.getDealerCode();
                    if (dd.getDealerId() != null) {
                        Dealer dlr = dealerRepository.selectById(dd.getDealerId());
                        if (dlr != null) {
                            name = dlr.getName();
                        }
                    }
                    chainNames.add(showDealerCommission ? name + "(" + dd.getCommissionRate() + "%)" : name);
                }
                map.put("vendorChain", String.join(" → ", chainNames));
                map.put("vendorChainList", showDealerCommission ? dealerChain : null);

                // 获取当前经销商名称
                if (currentDealer.getDealerId() != null) {
                    Dealer dlr = dealerRepository.selectById(currentDealer.getDealerId());
                    map.put("currentVendorName", dlr != null ? dlr.getName() : null);
                }
            }

            // 鍏煎鏃犻摼璺褰曠殑鏁版嵁锛氬洖閫€鍒?current_dealer_id
            if (map.get("currentVendorId") == null && d.getCurrentDealerId() != null) {
                Dealer currentDealer = dealerByIdMap.get(d.getCurrentDealerId());
                if (currentDealer != null) {
                    map.put("currentVendorId", currentDealer.getId());
                    map.put("currentVendorCode", currentDealer.getDealerCode());
                    map.put("currentVendorName", currentDealer.getName());
                }
            }

            resultList.add(map);
        }
        return resultList;
    }

    /**
     * 创建单个设备
     */
    @Transactional
    public ManufacturedDevice createDevice(ManufacturedDevice device) {
        // 验证设备ID是否已存在
        if (device.getDeviceId() != null) {
            QueryWrapper<ManufacturedDevice> qw = new QueryWrapper<>();
            qw.lambda().eq(ManufacturedDevice::getDeviceId, device.getDeviceId());
            if (deviceRepository.selectCount(qw) > 0) {
                throw new RuntimeException("设备ID已存在: " + device.getDeviceId());
            }
        }

        device.setCreatedAt(new Date());
        device.setUpdatedAt(new Date());
        if (device.getStatus() == null) {
            device.setStatus(ManufacturedDeviceStatus.MANUFACTURED);
        }
        deviceRepository.insert(device);
        return device;
    }

    /**
     * 更新设备信息
     */
    @Transactional
    public ManufacturedDevice updateDevice(Long id, ManufacturedDevice device) {
        ManufacturedDevice existing = deviceRepository.selectById(id);
        if (existing == null) {
            throw new RuntimeException("设备不存在");
        }

        device.setId(id);
        device.setUpdatedAt(new Date());
        // 不允许修改设备ID
        device.setDeviceId(null);
        deviceRepository.updateById(device);
        return deviceRepository.selectById(id);
    }

    /**
     * 批量删除设备
     */
    @Transactional
    public void deleteDevices(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new RuntimeException("设备ID列表不能为空");
        }
        deviceRepository.deleteBatchIds(ids);
    }

    /**
     * 激活设备
     * 用于用户绑定设备时调用，记录MAC地址、激活时间、上线国家
     *
     * @param deviceId   设备ID (16位)
     * @param macAddress MAC地址
     * @param country    上线所属国家
     * @return 更新后的设备信息
     */
    @Transactional
    public ManufacturedDevice activateDevice(String deviceId, String macAddress, String country) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            throw new RuntimeException("设备ID不能为空");
        }

        ManufacturedDevice device = getDevice(deviceId.trim());
        if (device == null) {
            throw new RuntimeException("设备不存在: " + deviceId);
        }

        // 更新设备信息
        device.setMacAddress(macAddress);
        device.setCountry(country);
        device.setActivatedAt(new Date());
        device.setStatus(ManufacturedDeviceStatus.ACTIVATED);
        device.setUpdatedAt(new Date());
        deviceRepository.updateById(device);

        log.info("设备激活成功: deviceId={}, macAddress={}, country={}", deviceId, macAddress, country);
        return device;
    }

    /**
     * 更新设备状态（禁用/启用）
     *
     * @param deviceId 设备ID (16位)
     * @param status   目标状态 (disabled/manufactured)
     * @return 更新后的设备信息
     */
    @Transactional
    public ManufacturedDevice updateDeviceStatus(String deviceId, String status) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            throw new RuntimeException("设备ID不能为空");
        }

        ManufacturedDevice device = getDevice(deviceId.trim());
        if (device == null) {
            throw new RuntimeException("设备不存在: " + deviceId);
        }

        ManufacturedDeviceStatus newStatus = ManufacturedDeviceStatus.fromCode(status);
        if (newStatus == null) {
            throw new RuntimeException("无效的状态值: " + status);
        }

        device.setStatus(newStatus);
        device.setUpdatedAt(new Date());
        deviceRepository.updateById(device);

        log.info("设备状态更新成功: deviceId={}, status={}", deviceId, status);
        return device;
    }

    /**
     * 获取所有设备列表（用于导出）
     */
    public List<ManufacturedDevice> listAllDevices(Long currentUserId, String deviceId, String batchNo, String status) {
        DeviceDataScope scope = resolveDeviceDataScope(currentUserId, null, null);
        if (!scope.hasRoleScope()) {
            return new ArrayList<>();
        }

        QueryWrapper<ManufacturedDevice> qw = new QueryWrapper<>();
        if (deviceId != null && !deviceId.trim().isEmpty()) {
            qw.lambda().like(ManufacturedDevice::getDeviceId, deviceId);
        }
        if (batchNo != null && !batchNo.trim().isEmpty()) {
            QueryWrapper<DeviceProductionBatch> batchQw = new QueryWrapper<>();
            batchQw.lambda().eq(DeviceProductionBatch::getBatchNo, batchNo);
            DeviceProductionBatch batch = batchRepository.selectOne(batchQw);
            if (batch != null) {
                qw.lambda().eq(ManufacturedDevice::getBatchId, batch.getId());
            } else {
                return new ArrayList<>();
            }
        }
        if (status != null && !status.trim().isEmpty()) {
            ManufacturedDeviceStatus statusEnum = ManufacturedDeviceStatus.fromCode(status);
            if (statusEnum != null) {
                qw.lambda().eq(ManufacturedDevice::getStatus, statusEnum);
            }
        }

        Set<String> effectiveDealerScopeIds = scope.dealerPermissionByChain ? scope.dealerManagedDeviceIds : Collections.emptySet();
        boolean useRoleOrScope = scope.installerRoleScope && scope.dealerRoleScope;
        applyRoleDeviceFilter(
                qw,
                scope.effectiveInstallerCode,
                scope.effectiveDealerCode,
                effectiveDealerScopeIds,
                useRoleOrScope
        );

        qw.lambda().orderByDesc(ManufacturedDevice::getCreatedAt);
        return deviceRepository.selectList(qw);
    }

    // ==================== 私有方法 ====================

    /**
     * 随机字符池：大小写字母 + 数字0-9（排除O和o，避免与数字0混淆）
     */
    private static final String RANDOM_CHARS = "ABCDEFGHIJKLMNPQRSTUVWXYZabcdefghijklmnpqrstuvwxyz0123456789";
    private static final Random RANDOM = new Random();

    /**
     * 生成批次号
     * 格式: PB + yyyyMMdd + 8位随机字符(大小写字母+数字0-9)
     * 例如: PB20241205aB3xYz9K
     */
    private String generateBatchNo() {
        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String prefix = "PB" + date;

        // 最多尝试10次生成唯一批次号
        for (int attempt = 0; attempt < 10; attempt++) {
            String randomSuffix = generateRandomString(8);
            String batchNo = prefix + randomSuffix;

            // 检查是否已存在
            QueryWrapper<DeviceProductionBatch> qw = new QueryWrapper<>();
            qw.lambda().eq(DeviceProductionBatch::getBatchNo, batchNo);
            if (batchRepository.selectCount(qw) == 0) {
                return batchNo;
            }
            log.warn("批次号冲突，重新生成: {}", batchNo);
        }
        throw new RuntimeException("无法生成唯一批次号，请稍后重试");
    }

    /**
     * 生成指定长度的随机字符串（大小写字母+数字，字符不重复）
     */
    private String generateRandomString(int length) {
        // 将字符池转为列表并打乱
        List<Character> chars = new ArrayList<>();
        for (char c : RANDOM_CHARS.toCharArray()) {
            chars.add(c);
        }
        Collections.shuffle(chars, RANDOM);

        // 取前length个字符
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.get(i));
        }
        return sb.toString();
    }

    /**
     * 计算给定前8位的下一个起始序列号
     * 查找现有最大序列号 + 1
     */
    private int calculateNextStartSerial(String networkLens, String deviceForm, String specialReq,
                                         String assemblerCode, String vendorCode, String reserved) {
        String prefix = networkLens + deviceForm + specialReq + assemblerCode + vendorCode + reserved;
        QueryWrapper<ManufacturedDevice> qw = new QueryWrapper<>();
        qw.lambda().likeRight(ManufacturedDevice::getDeviceId, prefix)
                .orderByDesc(ManufacturedDevice::getSerialNo)
                .last("limit 1");
        ManufacturedDevice last = deviceRepository.selectOne(qw);
        if (last == null) {
            return 1;
        }
        try {
            int lastNo = Integer.parseInt(last.getSerialNo());
            return lastNo + 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
