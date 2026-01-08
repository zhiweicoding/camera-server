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

import java.text.SimpleDateFormat;
import java.util.*;

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

    @Autowired
    private InstallerRepository installerRepository;

    @Autowired
    private DealerRepository dealerRepository;

    /**
     * 默认密码哈希(与admin2一致)
     */
    private static final String DEFAULT_PASSWORD_HASH = "$2a$10$Pw.m19MeKdWZvC.r5MrdiebsKTLcf6WS9fuWwmfAxX1IugFu2k.pS";

    // ==================== 经销商/销售商管理 ====================

    /**
     * 获取所有启用的经销商列表
     */
    public List<Vendor> listVendors() {
        QueryWrapper<Vendor> qw = new QueryWrapper<>();
        qw.lambda().eq(Vendor::getStatus, EnableStatus.ENABLED).orderByAsc(Vendor::getVendorCode);
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
     *
     * @param id 经销商ID
     * @return 经销商实体
     */
    public Vendor getVendorById(Long id) {
        return vendorRepository.selectById(id);
    }

    /**
     * 根据经销商代码获取经销商
     *
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
     *
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
            vendor.setStatus(EnableStatus.ENABLED); // 默认启用
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
        user.setEnabled(vendor.getStatus() != null ? vendor.getStatus().getCode() : EnableStatus.ENABLED.getCode());
        user.setCreatedAt(new Date());
        user.setUpdatedAt(new Date());
        userRepository.insert(user);

        log.info("新增经销商: code={}, name={}, userId={}", vendor.getVendorCode(), vendor.getVendorName(), user.getId());
        return vendor;
    }

    /**
     * 更新经销商信息
     *
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
                user.setEnabled(vendor.getStatus().getCode());
            }
            user.setUpdatedAt(new Date());
            userRepository.updateById(user);
        }

        log.info("更新经销商: id={}, code={}", vendor.getId(), vendor.getVendorCode());
        return vendorRepository.selectById(vendor.getId());
    }

    /**
     * 删除经销商（物理删除）
     *
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
     *
     * @param id     经销商ID
     * @param status 状态：1-启用, 0-禁用
     */
    @Transactional
    public void updateVendorStatus(Long id, Integer status) {
        Vendor vendor = vendorRepository.selectById(id);
        if (vendor == null) {
            throw new RuntimeException("经销商不存在");
        }
        vendor.setStatus(EnableStatus.fromCode(status));
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
     * 包括：网络镜头配置、设备形态、特殊要求、装机商、销售商
     */
    public Map<String, Object> getOptions() {
        Map<String, Object> map = new HashMap<>();
        // 网络+镜头配置（机身号第1-2位）
        map.put("network_lens", Arrays.asList("A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "R1"));
        // 设备形态（机身号第3位）
        map.put("device_form", Arrays.asList("1", "2", "3", "4", "5"));
        // 特殊要求（机身号第4位）
        map.put("special_req", Arrays.asList("0", "1", "2", "3"));
        // 预留位（机身号第8位）
        map.put("reserved", Collections.singletonList("0"));
        // 装机商列表（installer表，机身号第5位）
        map.put("installers", listInstallers());
        // 销售商列表（vendor表，机身号第6-7位），包含默认的00-先不指定选项
        List<Map<String, Object>> dealersWithDefault = new ArrayList<>();
        // 添加默认选项
        Map<String, Object> defaultDealer = new HashMap<>();
        defaultDealer.put("vendorCode", "00");
        defaultDealer.put("vendorName", "先不指定");
        dealersWithDefault.add(defaultDealer);
        // 添加实际经销商
        for (Vendor v : listVendors()) {
            Map<String, Object> dealer = new HashMap<>();
            dealer.put("vendorCode", v.getVendorCode());
            dealer.put("vendorName", v.getVendorName());
            dealer.put("id", v.getId());
            dealersWithDefault.add(dealer);
        }
        map.put("dealers", dealersWithDefault);
        // 保留旧字段兼容
        map.put("assemblers", listAssemblers());
        map.put("vendors", listVendors());
        return map;
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
     * 更新设备的经销商关联，同时修改设备ID的第6-7位
     *
     * @param deviceId   原设备ID（16位）
     * @param vendorCode 新的经销商代码（2位）
     * @return 包含新设备ID等信息的Map
     */
    @Transactional
    public Map<String, Object> scanAssignDealer(String deviceId, String vendorCode) {
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

        // 如果vendorCode不是"00"，校验经销商是否存在且启用
        if (!"00".equals(vendorCode)) {
            QueryWrapper<Vendor> vq = new QueryWrapper<>();
            vq.lambda().eq(Vendor::getVendorCode, vendorCode).eq(Vendor::getStatus, EnableStatus.ENABLED);
            if (vendorRepository.selectCount(vq) == 0) {
                throw new RuntimeException("经销商不存在或未启用: " + vendorCode);
            }
        }

        String oldDeviceId = device.getDeviceId();
        String oldVendorCode = device.getVendorCode();
        boolean vendorChanged = !vendorCode.equals(oldVendorCode);
        String newDeviceId = oldDeviceId;

        // 如果经销商代码变化，需要更新设备ID
        if (vendorChanged) {
            // 构建新的设备ID：第1-5位 + 新的第6-7位(vendorCode) + 第8-16位
            newDeviceId = oldDeviceId.substring(0, 5) + vendorCode + oldDeviceId.substring(7);

            // 检查新设备ID是否已存在（排除自身）
            QueryWrapper<ManufacturedDevice> checkQw = new QueryWrapper<>();
            checkQw.lambda().eq(ManufacturedDevice::getDeviceId, newDeviceId)
                    .ne(ManufacturedDevice::getId, device.getId());
            if (deviceRepository.selectCount(checkQw) > 0) {
                throw new RuntimeException("新设备ID已存在: " + newDeviceId);
            }

            // 更新设备信息
            device.setDeviceId(newDeviceId);
            device.setVendorCode(vendorCode);
            device.setUpdatedAt(new Date());
            deviceRepository.updateById(device);

            log.info("扫码分配经销商成功: oldDeviceId={}, newDeviceId={}, vendorCode={}",
                    oldDeviceId, newDeviceId, vendorCode);

            Map<String, Object> result = new HashMap<>();
            result.put("deviceId", oldDeviceId);
            result.put("newDeviceId", newDeviceId);
            result.put("vendorCode", vendorCode);
            result.put("changed", true);
            result.put("message", "分配成功");
            return result;
        }

        // 经销商未变化
        Map<String, Object> result = new HashMap<>();
        result.put("deviceId", oldDeviceId);
        result.put("newDeviceId", oldDeviceId);
        result.put("vendorCode", vendorCode);
        result.put("changed", false);
        result.put("message", "经销商未变化");
        return result;
    }

    /**
     * 批量分配经销商到设备（扫码分配）
     * 同时设置设备的经销商佣金比例
     *
     * @param deviceIds      设备ID列表
     * @param dealerCode     经销商代码（2位）
     * @param commissionRate 佣金比例
     * @return 处理结果
     */
    @Transactional
    public Map<String, Object> batchAssignDealer(List<String> deviceIds, String dealerCode, java.math.BigDecimal commissionRate) {
        // 校验经销商代码
        if (dealerCode == null || dealerCode.length() != 2) {
            throw new RuntimeException("经销商代码必须是2位");
        }

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
        int successCount = 0;
        int failCount = 0;

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

                String oldVendorCode = device.getVendorCode();
                boolean vendorChanged = !dealerCode.equals(oldVendorCode);
                String newDeviceId = deviceId;

                // 如果经销商代码变化，更新设备ID
                if (vendorChanged) {
                    newDeviceId = deviceId.substring(0, 5) + dealerCode + deviceId.substring(7);

                    // 检查新设备ID是否已存在
                    QueryWrapper<ManufacturedDevice> checkQw = new QueryWrapper<>();
                    checkQw.lambda().eq(ManufacturedDevice::getDeviceId, newDeviceId)
                            .ne(ManufacturedDevice::getId, device.getId());
                    if (deviceRepository.selectCount(checkQw) > 0) {
                        throw new RuntimeException("新设备ID已存在: " + newDeviceId);
                    }

                    device.setDeviceId(newDeviceId);
                    device.setVendorCode(dealerCode);
                }

                // 更新经销商ID和佣金比例
                device.setCurrentDealerId(dealer != null ? dealer.getId() : null);
                device.setDealerCommissionRate(commissionRate);
                device.setUpdatedAt(new Date());
                deviceRepository.updateById(device);

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
        return response;
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
        int quantity = request.getQuantity();

        // 校验经销商是否存在且启用
        QueryWrapper<Vendor> vq = new QueryWrapper<>();
        vq.lambda().eq(Vendor::getVendorCode, vendorCode).eq(Vendor::getStatus, EnableStatus.ENABLED);
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
        batch.setStatus(ProductionBatchStatus.COMPLETED);
        batch.setRemark(request.getRemark());
        batch.setCreatedBy(request.getCreatedBy());
        batch.setInstallerCommissionRate(request.getInstallerCommissionRate());
        batch.setDealerCommissionRate(request.getDealerCommissionRate());
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
     * 后8位使用随机字符（大小写字母+数字），确保唯一性
     */
    private void generateDevices(DeviceProductionBatch batch, String prefix, String networkLens, String deviceForm,
                                 String specialReq, String assemblerCode, String vendorCode,
                                 int startSerial, int endSerial) {
        int quantity = endSerial - startSerial + 1;
        Set<String> generatedSerials = new HashSet<>();
        
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
            device.setSerialNo(serial8);
            device.setStatus(ManufacturedDeviceStatus.MANUFACTURED);
            // 从批次复制分润比例
            device.setInstallerCommissionRate(batch.getInstallerCommissionRate());
            device.setDealerCommissionRate(batch.getDealerCommissionRate());
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
            ManufacturedDeviceStatus statusEnum = ManufacturedDeviceStatus.fromCode(status);
            if (statusEnum != null) {
                qw.lambda().eq(ManufacturedDevice::getStatus, statusEnum);
            }
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
            ManufacturedDeviceStatus statusEnum = ManufacturedDeviceStatus.fromCode(status);
            if (statusEnum != null) {
                countQw.lambda().eq(ManufacturedDevice::getStatus, statusEnum);
            }
        }
        if (vendorCode != null && !vendorCode.trim().isEmpty()) {
            countQw.lambda().eq(ManufacturedDevice::getVendorCode, vendorCode);
        }
        long total = deviceRepository.selectCount(countQw);

        // 获取装机商和经销商的分佣比例
        List<Map<String, Object>> deviceList = enrichDevicesWithCommission(devices);

        Map<String, Object> result = new HashMap<>();
        result.put("list", deviceList);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    /**
     * 为设备列表添加装机商和经销商的分佣比例信息
     */
    private List<Map<String, Object>> enrichDevicesWithCommission(List<ManufacturedDevice> devices) {
        if (devices == null || devices.isEmpty()) {
            return new ArrayList<>();
        }

        // 收集所有的 vendorCode 和 installerId
        Set<String> vendorCodes = new HashSet<>();
        Set<Long> installerIds = new HashSet<>();
        for (ManufacturedDevice d : devices) {
            if (d.getVendorCode() != null && !"00".equals(d.getVendorCode())) {
                vendorCodes.add(d.getVendorCode());
            }
            if (d.getInstallerId() != null) {
                installerIds.add(d.getInstallerId());
            }
        }

        // 批量查询 Vendor
        Map<String, Vendor> vendorMap = new HashMap<>();
        if (!vendorCodes.isEmpty()) {
            QueryWrapper<Vendor> vq = new QueryWrapper<>();
            vq.lambda().in(Vendor::getVendorCode, vendorCodes);
            List<Vendor> vendors = vendorRepository.selectList(vq);
            for (Vendor v : vendors) {
                vendorMap.put(v.getVendorCode(), v);
                // 同时收集经销商关联的装机商ID
                if (v.getInstallerId() != null) {
                    installerIds.add(v.getInstallerId());
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

        // 构建结果列表
        List<Map<String, Object>> resultList = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (ManufacturedDevice d : devices) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", d.getId());
            map.put("deviceId", d.getDeviceId());
            map.put("batchId", d.getBatchId());
            map.put("networkLens", d.getNetworkLens());
            map.put("deviceForm", d.getDeviceForm());
            map.put("specialReq", d.getSpecialReq());
            map.put("assemblerCode", d.getAssemblerCode());
            map.put("vendorCode", d.getVendorCode());
            map.put("installerId", d.getInstallerId());
            //map.put("currentVendorId", d.getCurrentVendorId());
            map.put("serialNo", d.getSerialNo());
            map.put("macAddress", d.getMacAddress());
            map.put("status", d.getStatus() != null ? d.getStatus().getCode() : null);
            map.put("country", d.getCountry());
            map.put("manufacturedAt", d.getManufacturedAt() != null ? sdf.format(d.getManufacturedAt()) : null);
            map.put("activatedAt", d.getActivatedAt() != null ? sdf.format(d.getActivatedAt()) : null);
            map.put("createdAt", d.getCreatedAt() != null ? sdf.format(d.getCreatedAt()) : null);
            map.put("updatedAt", d.getUpdatedAt() != null ? sdf.format(d.getUpdatedAt()) : null);

            // 添加装机商信息和分佣比例（从设备表读取）
            Installer installer = d.getInstallerId() != null ? installerMap.get(d.getInstallerId()) : null;
            if (installer != null) {
                map.put("installerName", installer.getInstallerName());
            } else {
                map.put("installerName", null);
            }
            // 分佣比例从设备表读取
            map.put("installerCommissionRate", d.getInstallerCommissionRate());

            // 添加经销商信息和分佣比例（从设备表读取）
            Vendor vendor = d.getVendorCode() != null ? vendorMap.get(d.getVendorCode()) : null;
            if (vendor != null) {
                map.put("salesmanName", vendor.getVendorName());
            } else {
                map.put("salesmanName", null);
            }
            // 分佣比例从设备表读取
            map.put("dealerCommissionRate", d.getDealerCommissionRate());

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
            ManufacturedDeviceStatus statusEnum = ManufacturedDeviceStatus.fromCode(status);
            if (statusEnum != null) {
                qw.lambda().eq(ManufacturedDevice::getStatus, statusEnum);
            }
        }
        qw.lambda().orderByDesc(ManufacturedDevice::getCreatedAt);
        return deviceRepository.selectList(qw);
    }

    // ==================== 私有方法 ====================

    /**
     * 随机字符池：大小写字母 + 数字0-9
     */
    private static final String RANDOM_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
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
