package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.domain.*;
import com.pura365.camera.model.CreateBatchRequest;
import com.pura365.camera.domain.Salesman;
import com.pura365.camera.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.UUID;

/**
 * 设备生产管理服务
 * 负责设备ID生成、生产批次管理、经销商/装机商管理
 */
@Service
public class DeviceProductionService {

    private static final Logger log = LoggerFactory.getLogger(DeviceProductionService.class);

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private AssemblerRepository assemblerRepository;

    @Autowired
    private DeviceProductionBatchRepository batchRepository;

    @Autowired
    private ManufacturedDeviceRepository deviceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SysDictService sysDictService;

    @Autowired
    private SalesmanRepository salesmanRepository;

    /** 默认密码哈希(与admin2一致) */
    private static final String DEFAULT_PASSWORD_HASH = "$2a$10$Pw.m19MeKdWZvC.r5MrdiebsKTLcf6WS9fuWwmfAxX1IugFu2k.pS";

    // ==================== 经销商/销售商管理 ====================

    /**
     * 获取所有启用的经销商列表
     */
    public List<Vendor> listVendors() {
        QueryWrapper<Vendor> qw = new QueryWrapper<>();
        qw.lambda().eq(Vendor::getStatus, 1).orderByAsc(Vendor::getVendorCode);
        return vendorRepository.selectList(qw);
    }

    /**
     * 获取所有经销商列表（包含禁用的）
     */
    public List<Vendor> listAllVendors() {
        QueryWrapper<Vendor> qw = new QueryWrapper<>();
        qw.lambda().orderByAsc(Vendor::getVendorCode);
        return vendorRepository.selectList(qw);
    }

    /**
     * 根据ID获取经销商
     * @param id 经销商ID
     * @return 经销商实体
     */
    public Vendor getVendorById(Long id) {
        return vendorRepository.selectById(id);
    }

    /**
     * 根据经销商代码获取经销商
     * @param vendorCode 经销商代码（2位）
     * @return 经销商实体
     */
    public Vendor getVendorByCode(String vendorCode) {
        QueryWrapper<Vendor> qw = new QueryWrapper<>();
        qw.lambda().eq(Vendor::getVendorCode, vendorCode);
        return vendorRepository.selectOne(qw);
    }

    /**
     * 新增经销商
     * @param vendor 经销商信息
     * @return 新增后的经销商实体
     */
    @Transactional
    public Vendor createVendor(Vendor vendor) {
        // 校验经销商代码
        if (vendor.getVendorCode() == null || vendor.getVendorCode().length() != 2) {
            throw new RuntimeException("经销商代码必须是2位");
        }
        if (vendor.getVendorName() == null || vendor.getVendorName().trim().isEmpty()) {
            throw new RuntimeException("经销商名称不能为空");
        }
        // 检查代码是否已存在
        if (getVendorByCode(vendor.getVendorCode()) != null) {
            throw new RuntimeException("经销商代码已存在");
        }
        // 检查用户名是否已存在
        QueryWrapper<User> userQw = new QueryWrapper<>();
        userQw.lambda().eq(User::getUsername, vendor.getVendorCode());
        if (userRepository.selectCount(userQw) > 0) {
            throw new RuntimeException("该经销商代码对应的用户已存在");
        }
        if (vendor.getStatus() == null) {
            vendor.setStatus(1); // 默认启用
        }
        vendorRepository.insert(vendor);

        // 同步创建user表记录
        User user = new User();
        user.setUid("vendor_" + System.currentTimeMillis());
        user.setUsername(vendor.getVendorCode());
        user.setPasswordHash(DEFAULT_PASSWORD_HASH);
        user.setRole(2); // 经销商角色
        user.setNickname(vendor.getVendorName());
        user.setPhone(vendor.getContactPhone());
        user.setEnabled(vendor.getStatus());
        user.setCreatedAt(new Date());
        user.setUpdatedAt(new Date());
        userRepository.insert(user);

        log.info("新增经销商: code={}, name={}, userId={}", vendor.getVendorCode(), vendor.getVendorName(), user.getId());
        return vendor;
    }

    /**
     * 更新经销商信息
     * @param vendor 经销商信息
     * @return 更新后的经销商实体
     */
    @Transactional
    public Vendor updateVendor(Vendor vendor) {
        if (vendor.getId() == null) {
            throw new RuntimeException("经销商ID不能为空");
        }
        Vendor existing = vendorRepository.selectById(vendor.getId());
        if (existing == null) {
            throw new RuntimeException("经销商不存在");
        }
        // 如果修改了代码，检查新代码是否已被其他经销商使用
        if (vendor.getVendorCode() != null && !vendor.getVendorCode().equals(existing.getVendorCode())) {
            Vendor codeExist = getVendorByCode(vendor.getVendorCode());
            if (codeExist != null && !codeExist.getId().equals(vendor.getId())) {
                throw new RuntimeException("经销商代码已被其他经销商使用");
            }
        }
        vendorRepository.updateById(vendor);

        // 同步更新user表记录
        QueryWrapper<User> userQw = new QueryWrapper<>();
        userQw.lambda().eq(User::getUsername, existing.getVendorCode());
        User user = userRepository.selectOne(userQw);
        if (user != null) {
            // 如果vendorCode变了，更新username
            if (vendor.getVendorCode() != null && !vendor.getVendorCode().equals(existing.getVendorCode())) {
                user.setUsername(vendor.getVendorCode());
            }
            if (vendor.getVendorName() != null) {
                user.setNickname(vendor.getVendorName());
            }
            if (vendor.getContactPhone() != null) {
                user.setPhone(vendor.getContactPhone());
            }
            if (vendor.getStatus() != null) {
                user.setEnabled(vendor.getStatus());
            }
            user.setUpdatedAt(new Date());
            userRepository.updateById(user);
        }

        log.info("更新经销商: id={}, code={}", vendor.getId(), vendor.getVendorCode());
        return vendorRepository.selectById(vendor.getId());
    }

    /**
     * 删除经销商（物理删除）
     * @param id 经销商ID
     */
    @Transactional
    public void deleteVendor(Long id) {
        Vendor vendor = vendorRepository.selectById(id);
        if (vendor == null) {
            throw new RuntimeException("经销商不存在");
        }
        // 检查是否有关联的生产设备
        QueryWrapper<ManufacturedDevice> qw = new QueryWrapper<>();
        qw.lambda().eq(ManufacturedDevice::getVendorCode, vendor.getVendorCode());
        if (deviceRepository.selectCount(qw) > 0) {
            throw new RuntimeException("该经销商已有关联的生产设备，无法删除，请改为禁用");
        }

        // 同步删除user表记录
        QueryWrapper<User> userQw = new QueryWrapper<>();
        userQw.lambda().eq(User::getUsername, vendor.getVendorCode());
        userRepository.delete(userQw);

        vendorRepository.deleteById(id);
        log.info("删除经销商: id={}, code={}", id, vendor.getVendorCode());
    }

    /**
     * 启用/禁用经销商
     * @param id 经销商ID
     * @param status 状态：1-启用, 0-禁用
     */
    @Transactional
    public void updateVendorStatus(Long id, Integer status) {
        Vendor vendor = vendorRepository.selectById(id);
        if (vendor == null) {
            throw new RuntimeException("经销商不存在");
        }
        vendor.setStatus(status);
        vendorRepository.updateById(vendor);

        // 同步更新user表状态
        QueryWrapper<User> userQw = new QueryWrapper<>();
        userQw.lambda().eq(User::getUsername, vendor.getVendorCode());
        User user = userRepository.selectOne(userQw);
        if (user != null) {
            user.setEnabled(status);
            user.setUpdatedAt(new Date());
            userRepository.updateById(user);
        }

        log.info("更新经销商状态: id={}, status={}", id, status);
    }

    // ==================== 装机商管理 ====================

    /**
     * 获取所有启用的装机商列表
     */
    public List<Assembler> listAssemblers() {
        QueryWrapper<Assembler> qw = new QueryWrapper<>();
        qw.lambda().eq(Assembler::getStatus, 1).orderByAsc(Assembler::getAssemblerCode);
        return assemblerRepository.selectList(qw);
    }

    // ==================== 配置选项 ====================

    /**
     * 获取设备ID生成的所有配置选项
     * 包括：网络镜头配置、设备形态、特殊要求、装机商、经销商
     * 现在从字典表读取，如果字典表为空则使用默认值
     */
    public Map<String, Object> getOptions() {
        Map<String, Object> map = new HashMap<>();
        
        // 从字典表获取选项
        Map<String, List<Map<String, String>>> dictOptions = sysDictService.getDeviceIdOptions();
        
        // 网络+镜头配置
        List<Map<String, String>> networkLens = dictOptions.get("network_lens");
        if (networkLens != null && !networkLens.isEmpty()) {
            map.put("network_lens", networkLens);
        } else {
            map.put("network_lens", Arrays.asList("A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "R1"));
        }
        
        // 设备形态
        List<Map<String, String>> deviceForm = dictOptions.get("device_form");
        if (deviceForm != null && !deviceForm.isEmpty()) {
            map.put("device_form", deviceForm);
        } else {
            map.put("device_form", Arrays.asList("1", "2", "3", "4", "5"));
        }
        
        // 特殊要求
        List<Map<String, String>> specialReq = dictOptions.get("special_req");
        if (specialReq != null && !specialReq.isEmpty()) {
            map.put("special_req", specialReq);
        } else {
            map.put("special_req", Arrays.asList("0", "1", "2", "3"));
        }
        
        // 预留位
        List<Map<String, String>> reserved = dictOptions.get("reserved");
        if (reserved != null && !reserved.isEmpty()) {
            map.put("reserved", reserved);
        } else {
            map.put("reserved", Collections.singletonList("0"));
        }
        
        // 装机商代码（从字典表读取）
        List<Map<String, String>> assemblerCode = dictOptions.get("assembler_code");
        if (assemblerCode != null && !assemblerCode.isEmpty()) {
            map.put("assembler_code", assemblerCode);
        }
        
        // 装机商列表（保留旧接口兼容）
        map.put("assemblers", listAssemblers());
        // 经销商列表
        map.put("vendors", listVendors());
        return map;
    }

    // ==================== 业务员分配 ====================

    /**
     * 分配业务员到设备
     * @param deviceId 设备ID
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
        device.setSalesmanId(salesmanId);
        device.setUpdatedAt(new Date());
        deviceRepository.updateById(device);
        log.info("分配业务员: deviceId={}, salesmanId={}", deviceId, salesmanId);
    }

    /**
     * 批量分配业务员到设备
     * @param deviceIds 设备ID列表
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
                device.setSalesmanId(salesmanId);
                device.setUpdatedAt(new Date());
                deviceRepository.updateById(device);
            }
        }
        log.info("批量分配业务员: deviceCount={}, salesmanId={}", deviceIds.size(), salesmanId);
    }

    /**
     * 移除设备的业务员分配
     * @param deviceId 设备ID
     */
    @Transactional
    public void removeSalesmanAssignment(String deviceId) {
        ManufacturedDevice device = getDevice(deviceId);
        if (device == null) {
            throw new RuntimeException("设备不存在");
        }
        device.setSalesmanId(null);
        device.setUpdatedAt(new Date());
        deviceRepository.updateById(device);
        log.info("移除业务员分配: deviceId={}", deviceId);
    }

    // ==================== 生产批次管理 ====================

    /**
     * 创建生产批次并批量生成设备ID入库
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
        int quantity = request.getQuantity();

        // 校验经销商是否存在且启用
        QueryWrapper<Vendor> vq = new QueryWrapper<>();
        vq.lambda().eq(Vendor::getVendorCode, vendorCode).eq(Vendor::getStatus, 1);
        if (vendorRepository.selectCount(vq) == 0) {
            throw new RuntimeException("经销商不存在或未启用");
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
        batch.setStatus("completed");
        batch.setRemark(request.getRemark());
        batch.setCreatedBy(request.getCreatedBy());
        batchRepository.insert(batch);

        // 批量生成设备ID
        String prefix = batch.getDeviceIdPrefix();
        generateDevices(batch.getId(), prefix, networkLens, deviceForm, specialReq, 
                assemblerCode, vendorCode, startSerial, endSerial);

        log.info("创建生产批次成功: batchNo={}, quantity={}, startSerial={}, endSerial={}", 
                batchNo, quantity, startSerial, endSerial);
        return batch;
    }

    /**
     * 校验创建批次请求参数
     */
    private void validateBatchRequest(CreateBatchRequest request) {
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
     * 批量生成设备记录
     */
    private void generateDevices(Long batchId, String prefix, String networkLens, String deviceForm,
                                  String specialReq, String assemblerCode, String vendorCode,
                                  int startSerial, int endSerial) {
        for (int i = startSerial; i <= endSerial; i++) {
            String serial8 = String.format("%08d", i);
            String deviceId = prefix + serial8;

            ManufacturedDevice device = new ManufacturedDevice();
            device.setBatchId(batchId);
            device.setDeviceId(deviceId);
            device.setNetworkLens(networkLens);
            device.setDeviceForm(deviceForm);
            device.setSpecialReq(specialReq);
            device.setAssemblerCode(assemblerCode);
            device.setVendorCode(vendorCode);
            device.setSerialNo(serial8);
            device.setStatus("manufactured");
            deviceRepository.insert(device);
        }
    }

    /**
     * 根据批次号获取批次信息
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
     * @param deviceId 设备ID (16位)
     * @return 设备实体
     */
    public ManufacturedDevice getDevice(String deviceId) {
        QueryWrapper<ManufacturedDevice> qw = new QueryWrapper<>();
        qw.lambda().eq(ManufacturedDevice::getDeviceId, deviceId);
        return deviceRepository.selectOne(qw);
    }

    /**
     * 分页查询设备列表
     */
    public Map<String, Object> listDevices(Integer page, Integer size, String deviceId, String batchNo, String status, String vendorCode) {
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
                    // 批次不存在，返回空列表
                    Map<String, Object> result = new HashMap<>();
                    result.put("list", new ArrayList<>());
                    result.put("total", 0);
                    result.put("page", page);
                    result.put("size", size);
                    return result;
                }
            }
        }
        if (status != null && !status.trim().isEmpty()) {
            qw.lambda().eq(ManufacturedDevice::getStatus, status);
        }
        if (vendorCode != null && !vendorCode.trim().isEmpty()) {
            qw.lambda().eq(ManufacturedDevice::getVendorCode, vendorCode);
        }
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
            countQw.lambda().eq(ManufacturedDevice::getStatus, status);
        }
        if (vendorCode != null && !vendorCode.trim().isEmpty()) {
            countQw.lambda().eq(ManufacturedDevice::getVendorCode, vendorCode);
        }
        long total = deviceRepository.selectCount(countQw);
        
        Map<String, Object> result = new HashMap<>();
        result.put("list", devices);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
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
        if (device.getStatus() == null || device.getStatus().trim().isEmpty()) {
            device.setStatus("manufactured");
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
     * 获取所有设备列表（用于导出）
     */
    public List<ManufacturedDevice> listAllDevices(String deviceId, String batchNo, String status) {
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
            qw.lambda().eq(ManufacturedDevice::getStatus, status);
        }
        qw.lambda().orderByDesc(ManufacturedDevice::getCreatedAt);
        return deviceRepository.selectList(qw);
    }

    // ==================== 私有方法 ====================

    /**
     * 生成批次号
     * 格式: PB + yyyyMMdd + 3位序号
     * 例如: PB20241205001
     */
    private String generateBatchNo() {
        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        QueryWrapper<DeviceProductionBatch> qw = new QueryWrapper<>();
        qw.lambda().likeRight(DeviceProductionBatch::getBatchNo, "PB" + date);
        long count = batchRepository.selectCount(qw);
        return String.format("PB%s%03d", date, count + 1);
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
