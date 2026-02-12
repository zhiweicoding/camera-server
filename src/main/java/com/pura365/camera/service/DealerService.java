package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.domain.Dealer;
import com.pura365.camera.domain.DeviceDealer;
import com.pura365.camera.domain.Installer;
import com.pura365.camera.enums.EnableStatus;
import com.pura365.camera.repository.DealerRepository;
import com.pura365.camera.repository.DeviceDealerRepository;
import com.pura365.camera.repository.InstallerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 经销商管理服务
 * 支持多级经销商层级结构
 */
@Service
public class DealerService {

    private static final Logger log = LoggerFactory.getLogger(DealerService.class);

    @Autowired
    private DealerRepository dealerRepository;

    @Autowired
    private InstallerRepository installerRepository;

    @Autowired
    private DeviceDealerRepository deviceDealerRepository;

    /**
     * 获取所有启用的经销商列表
     */
    public List<Dealer> listAllActive() {
        QueryWrapper<Dealer> qw = new QueryWrapper<>();
        qw.lambda().eq(Dealer::getStatus, EnableStatus.ENABLED)
                .orderByAsc(Dealer::getDealerCode);
        return dealerRepository.selectList(qw);
    }

    /**
     * 分页查询经销商
     */
    public Map<String, Object> listDealers(Integer page, Integer size, Long installerId, 
                                           String installerCode, String name, Integer status) {
        QueryWrapper<Dealer> qw = new QueryWrapper<>();
        if (name != null && !name.trim().isEmpty()) {
            qw.lambda().like(Dealer::getName, name);
        }
        if (status != null) {
            qw.lambda().eq(Dealer::getStatus, EnableStatus.fromCode(status));
        }
        qw.lambda().orderByDesc(Dealer::getCreatedAt);

        // 分页查询
        int offset = (page - 1) * size;
        qw.last("LIMIT " + offset + ", " + size);
        List<Dealer> dealers = dealerRepository.selectList(qw);

        // 转换为VO
        List<Map<String, Object>> list = dealers.stream().map(dealer -> {
            Map<String, Object> vo = new HashMap<>();
            vo.put("id", dealer.getId());
            vo.put("dealerCode", dealer.getDealerCode());
            vo.put("name", dealer.getName());
            vo.put("phone", dealer.getPhone());
            vo.put("level", dealer.getLevel());
            vo.put("status", dealer.getStatus());
            vo.put("remark", dealer.getRemark());
            // 企业信息字段
            vo.put("companyName", dealer.getCompanyName());
            vo.put("registeredCapital", dealer.getRegisteredCapital());
            vo.put("creditCode", dealer.getCreditCode());
            vo.put("registeredAddress", dealer.getRegisteredAddress());
            vo.put("businessLicense", dealer.getBusinessLicense());
            vo.put("createdAt", dealer.getCreatedAt());
            vo.put("updatedAt", dealer.getUpdatedAt());
            return vo;
        }).collect(java.util.stream.Collectors.toList());

        // 查询总数
        QueryWrapper<Dealer> countQw = new QueryWrapper<>();
        if (name != null && !name.trim().isEmpty()) {
            countQw.lambda().like(Dealer::getName, name);
        }
        if (status != null) {
            countQw.lambda().eq(Dealer::getStatus, EnableStatus.fromCode(status));
        }
        long total = dealerRepository.selectCount(countQw);

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    /**
     * 根据ID获取经销商
     */
    public Dealer getById(Long id) {
        return dealerRepository.selectById(id);
    }

    /**
     * 根据代号获取经销商
     */
    public Dealer getByCode(String dealerCode) {
        if (dealerCode == null || dealerCode.trim().isEmpty()) {
            return null;
        }
        QueryWrapper<Dealer> qw = new QueryWrapper<>();
        qw.lambda().eq(Dealer::getDealerCode, dealerCode);
        return dealerRepository.selectOne(qw);
    }

    /**
     * 创建经销商
     */
    @Transactional
    public Dealer create(Dealer dealer) {
        // 验证名称
        if (dealer.getName() == null || dealer.getName().trim().isEmpty()) {
            throw new RuntimeException("经销商名称不能为空");
        }

        // 验证代号长度（必须为2位）和唯一性
        if (dealer.getDealerCode() != null && !dealer.getDealerCode().trim().isEmpty()) {
            if (dealer.getDealerCode().length() != 2) {
                throw new RuntimeException("经销商代号必须为2位");
            }
            if ("00".equals(dealer.getDealerCode())) {
                throw new RuntimeException("经销商代码00为系统保留值，不能使用");
            }
            Dealer existing = getByCode(dealer.getDealerCode());
            if (existing != null) {
                throw new RuntimeException("经销商代号已存在: " + dealer.getDealerCode());
            }
        }

        // 经销商是独立身份，默认层级1
        dealer.setLevel(1);

        if (dealer.getStatus() == null) {
            dealer.setStatus(EnableStatus.ENABLED);
        }
        dealer.setCreatedAt(new Date());
        dealer.setUpdatedAt(new Date());
        dealerRepository.insert(dealer);
        log.info("创建经销商: id={}, name={}, dealerCode={}", 
                dealer.getId(), dealer.getName(), dealer.getDealerCode());
        return dealer;
    }

    /**
     * 更新经销商
     */
    @Transactional
    public Dealer update(Long id, Dealer dealer) {
        Dealer existing = dealerRepository.selectById(id);
        if (existing == null) {
            throw new RuntimeException("经销商不存在");
        }

        // 如果修改了代号，检查长度（必须为2位）和唯一性
        if (dealer.getDealerCode() != null && !dealer.getDealerCode().equals(existing.getDealerCode())) {
            if (dealer.getDealerCode().length() != 2) {
                throw new RuntimeException("经销商代号必须为2位");
            }
            if ("00".equals(dealer.getDealerCode())) {
                throw new RuntimeException("经销商代码00为系统保留值，不能使用");
            }
            Dealer byCode = getByCode(dealer.getDealerCode());
            if (byCode != null && !byCode.getId().equals(id)) {
                throw new RuntimeException("经销商代号已存在: " + dealer.getDealerCode());
            }
            existing.setDealerCode(dealer.getDealerCode());
        }

        if (dealer.getName() != null) {
            existing.setName(dealer.getName());
        }
        if (dealer.getPhone() != null) {
            existing.setPhone(dealer.getPhone());
        }
        if (dealer.getRemark() != null) {
            existing.setRemark(dealer.getRemark());
        }
        if (dealer.getStatus() != null) {
            existing.setStatus(dealer.getStatus());
        }
        // 企业信息字段
        if (dealer.getCompanyName() != null) {
            existing.setCompanyName(dealer.getCompanyName());
        }
        if (dealer.getRegisteredCapital() != null) {
            existing.setRegisteredCapital(dealer.getRegisteredCapital());
        }
        if (dealer.getCreditCode() != null) {
            existing.setCreditCode(dealer.getCreditCode());
        }
        if (dealer.getRegisteredAddress() != null) {
            existing.setRegisteredAddress(dealer.getRegisteredAddress());
        }
        if (dealer.getBusinessLicense() != null) {
            existing.setBusinessLicense(dealer.getBusinessLicense());
        }

        // 不允许修改装机商归属和层级
        existing.setUpdatedAt(new Date());
        dealerRepository.updateById(existing);
        log.info("更新经销商: id={}", id);
        return existing;
    }

    /**
     * 删除经销商
     */
    @Transactional
    public void delete(Long id) {
        Dealer dealer = dealerRepository.selectById(id);
        if (dealer == null) {
            throw new RuntimeException("经销商不存在");
        }

        // 检查是否有下级经销商
        QueryWrapper<Dealer> subQw = new QueryWrapper<>();
        subQw.lambda().eq(Dealer::getParentDealerId, id);
        if (dealerRepository.selectCount(subQw) > 0) {
            throw new RuntimeException("该经销商有下级经销商，无法删除");
        }

        // 检查是否有关联设备
        QueryWrapper<DeviceDealer> deviceQw = new QueryWrapper<>();
        deviceQw.lambda().eq(DeviceDealer::getDealerId, id);
        if (deviceDealerRepository.selectCount(deviceQw) > 0) {
            throw new RuntimeException("该经销商已有关联设备，无法删除，请改为禁用");
        }

        dealerRepository.deleteById(id);
        log.info("删除经销商: id={}, name={}", id, dealer.getName());
    }

    /**
     * 更新经销商状态
     */
    @Transactional
    public void updateStatus(Long id, Integer status) {
        Dealer dealer = dealerRepository.selectById(id);
        if (dealer == null) {
            throw new RuntimeException("经销商不存在");
        }
        dealer.setStatus(EnableStatus.fromCode(status));
        dealer.setUpdatedAt(new Date());
        dealerRepository.updateById(dealer);
        log.info("更新经销商状态: id={}, status={}", id, status);
    }
}
