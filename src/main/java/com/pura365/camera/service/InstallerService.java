package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.domain.Installer;
import com.pura365.camera.domain.ManufacturedDevice;
import com.pura365.camera.enums.EnableStatus;
import com.pura365.camera.repository.InstallerRepository;
import com.pura365.camera.repository.ManufacturedDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 装机商管理服务
 */
@Service
public class InstallerService {

    private static final Logger log = LoggerFactory.getLogger(InstallerService.class);

    @Autowired
    private InstallerRepository installerRepository;

    @Autowired
    private ManufacturedDeviceRepository deviceRepository;

    /**
     * 获取所有启用的装机商列表
     */
    public List<Installer> listEnabled() {
        QueryWrapper<Installer> qw = new QueryWrapper<>();
        qw.lambda().eq(Installer::getStatus, EnableStatus.ENABLED)
                .orderByAsc(Installer::getInstallerCode);
        return installerRepository.selectList(qw);
    }

    /**
     * 获取所有装机商列表（包含禁用的）
     */
    public List<Installer> listAll() {
        QueryWrapper<Installer> qw = new QueryWrapper<>();
        qw.lambda().orderByAsc(Installer::getInstallerCode);
        return installerRepository.selectList(qw);
    }

    /**
     * 分页查询装机商
     */
    public Map<String, Object> listInstallers(Integer page, Integer size, String installerCode, String name, Integer status) {
        QueryWrapper<Installer> qw = new QueryWrapper<>();
        if (installerCode != null && !installerCode.trim().isEmpty()) {
            qw.lambda().like(Installer::getInstallerCode, installerCode);
        }
        if (name != null && !name.trim().isEmpty()) {
            qw.lambda().like(Installer::getInstallerName, name);
        }
        if (status != null) {
            qw.lambda().eq(Installer::getStatus, EnableStatus.fromCode(status));
        }
        qw.lambda().orderByDesc(Installer::getCreatedAt);

        // 分页查询
        int offset = (page - 1) * size;
        qw.last("LIMIT " + offset + ", " + size);
        List<Installer> list = installerRepository.selectList(qw);

        // 查询总数
        QueryWrapper<Installer> countQw = new QueryWrapper<>();
        if (installerCode != null && !installerCode.trim().isEmpty()) {
            countQw.lambda().like(Installer::getInstallerCode, installerCode);
        }
        if (name != null && !name.trim().isEmpty()) {
            countQw.lambda().like(Installer::getInstallerName, name);
        }
        if (status != null) {
            countQw.lambda().eq(Installer::getStatus, EnableStatus.fromCode(status));
        }
        long total = installerRepository.selectCount(countQw);

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    /**
     * 根据ID获取装机商
     */
    public Installer getById(Long id) {
        return installerRepository.selectById(id);
    }

    /**
     * 根据代码获取装机商
     */
    public Installer getByCode(String installerCode) {
        QueryWrapper<Installer> qw = new QueryWrapper<>();
        qw.lambda().eq(Installer::getInstallerCode, installerCode);
        return installerRepository.selectOne(qw);
    }

    /**
     * 创建装机商
     */
    @Transactional
    public Installer create(Installer installer) {
        // 验证代码不能重复
        if (installer.getInstallerCode() == null || installer.getInstallerCode().trim().isEmpty()) {
            throw new RuntimeException("装机商代码不能为空");
        }
        Installer existing = getByCode(installer.getInstallerCode());
        if (existing != null) {
            throw new RuntimeException("装机商代码已存在: " + installer.getInstallerCode());
        }

        if (installer.getInstallerName() == null || installer.getInstallerName().trim().isEmpty()) {
            throw new RuntimeException("装机商名称不能为空");
        }

        if (installer.getStatus() == null) {
            installer.setStatus(EnableStatus.ENABLED);
        }
        installer.setCreatedAt(new Date());
        installer.setUpdatedAt(new Date());
        installerRepository.insert(installer);
        log.info("创建装机商: id={}, code={}, name={}", installer.getId(), installer.getInstallerCode(), installer.getInstallerName());
        return installer;
    }

    /**
     * 更新装机商
     */
    @Transactional
    public Installer update(Long id, Installer installer) {
        Installer existing = installerRepository.selectById(id);
        if (existing == null) {
            throw new RuntimeException("装机商不存在");
        }

        // 如果修改了代码，检查重复
        if (installer.getInstallerCode() != null && !installer.getInstallerCode().equals(existing.getInstallerCode())) {
            Installer byCode = getByCode(installer.getInstallerCode());
            if (byCode != null) {
                throw new RuntimeException("装机商代码已存在: " + installer.getInstallerCode());
            }
            existing.setInstallerCode(installer.getInstallerCode());
        }

        if (installer.getInstallerName() != null) {
            existing.setInstallerName(installer.getInstallerName());
        }
        if (installer.getContactPerson() != null) {
            existing.setContactPerson(installer.getContactPerson());
        }
        if (installer.getContactPhone() != null) {
            existing.setContactPhone(installer.getContactPhone());
        }
        if (installer.getAddress() != null) {
            existing.setAddress(installer.getAddress());
        }
        if (installer.getStatus() != null) {
            existing.setStatus(installer.getStatus());
        }
        // 企业信息字段
        if (installer.getCompanyName() != null) {
            existing.setCompanyName(installer.getCompanyName());
        }
        if (installer.getRegisteredCapital() != null) {
            existing.setRegisteredCapital(installer.getRegisteredCapital());
        }
        if (installer.getCreditCode() != null) {
            existing.setCreditCode(installer.getCreditCode());
        }
        if (installer.getRegisteredAddress() != null) {
            existing.setRegisteredAddress(installer.getRegisteredAddress());
        }
        if (installer.getBusinessLicense() != null) {
            existing.setBusinessLicense(installer.getBusinessLicense());
        }

        existing.setUpdatedAt(new Date());
        installerRepository.updateById(existing);
        log.info("更新装机商: id={}", id);
        return existing;
    }

    /**
     * 删除装机商
     */
    @Transactional
    public void delete(Long id) {
        Installer installer = installerRepository.selectById(id);
        if (installer == null) {
            throw new RuntimeException("装机商不存在");
        }

        // 检查是否有关联设备
        QueryWrapper<ManufacturedDevice> qw = new QueryWrapper<>();
        qw.lambda().eq(ManufacturedDevice::getInstallerId, id);
        if (deviceRepository.selectCount(qw) > 0) {
            throw new RuntimeException("该装机商已有关联设备，无法删除，请改为禁用");
        }

        installerRepository.deleteById(id);
        log.info("删除装机商: id={}, code={}", id, installer.getInstallerCode());
    }

    /**
     * 更新装机商状态
     */
    @Transactional
    public void updateStatus(Long id, Integer status) {
        Installer installer = installerRepository.selectById(id);
        if (installer == null) {
            throw new RuntimeException("装机商不存在");
        }
        installer.setStatus(EnableStatus.fromCode(status));
        installer.setUpdatedAt(new Date());
        installerRepository.updateById(installer);
        log.info("更新装机商状态: id={}, status={}", id, status);
    }
}
