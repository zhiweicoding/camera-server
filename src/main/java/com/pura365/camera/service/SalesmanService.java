package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.domain.ManufacturedDevice;
import com.pura365.camera.domain.Salesman;
import com.pura365.camera.domain.Vendor;
import com.pura365.camera.enums.EnableStatus;
import com.pura365.camera.repository.ManufacturedDeviceRepository;
import com.pura365.camera.repository.SalesmanRepository;
import com.pura365.camera.repository.VendorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 业务员管理服务
 */
@Service
public class SalesmanService {

    private static final Logger log = LoggerFactory.getLogger(SalesmanService.class);

    @Autowired
    private SalesmanRepository salesmanRepository;

    @Autowired
    private ManufacturedDeviceRepository deviceRepository;

    @Autowired
    private VendorRepository vendorRepository;

    /**
     * 获取经销商下的所有业务员列表
     * @param vendorId 经销商ID
     * @return 业务员列表
     */
    public List<Salesman> listByVendor(Long vendorId) {
        QueryWrapper<Salesman> qw = new QueryWrapper<>();
        qw.lambda().eq(Salesman::getVendorId, vendorId)
                .orderByAsc(Salesman::getName);
        return salesmanRepository.selectList(qw);
    }

    /**
     * 获取经销商下启用的业务员列表
     * @param vendorId 经销商ID
     * @return 业务员列表
     */
    public List<Salesman> listActiveByVendor(Long vendorId) {
        QueryWrapper<Salesman> qw = new QueryWrapper<>();
        qw.lambda().eq(Salesman::getVendorId, vendorId)
                .eq(Salesman::getStatus, EnableStatus.ENABLED)
                .orderByAsc(Salesman::getName);
        return salesmanRepository.selectList(qw);
    }

    /**
     * 根据经销商代码获取业务员列表
     * @param vendorCode 经销商代码
     * @return 业务员列表
     */
    public List<Salesman> listByVendorCode(String vendorCode) {
        QueryWrapper<Salesman> qw = new QueryWrapper<>();
        qw.lambda().eq(Salesman::getVendorCode, vendorCode)
                .eq(Salesman::getStatus, EnableStatus.ENABLED)
                .orderByAsc(Salesman::getName);
        return salesmanRepository.selectList(qw);
    }

    /**
     * 分页查询业务员（管理后台用）
     */
    public Map<String, Object> listSalesmen(Integer page, Integer size, Long vendorId, String vendorCode, String name, Integer status) {
        QueryWrapper<Salesman> qw = new QueryWrapper<>();
        if (vendorId != null) {
            qw.lambda().eq(Salesman::getVendorId, vendorId);
        }
        if (vendorCode != null && !vendorCode.trim().isEmpty()) {
            qw.lambda().eq(Salesman::getVendorCode, vendorCode);
        }
        if (name != null && !name.trim().isEmpty()) {
            qw.lambda().like(Salesman::getName, name);
        }
        if (status != null) {
            qw.lambda().eq(Salesman::getStatus, EnableStatus.fromCode(status));
        }
        qw.lambda().orderByDesc(Salesman::getCreatedAt);

        // 分页查询
        int offset = (page - 1) * size;
        qw.last("LIMIT " + offset + ", " + size);
        List<Salesman> list = salesmanRepository.selectList(qw);

        // 查询总数
        QueryWrapper<Salesman> countQw = new QueryWrapper<>();
        if (vendorId != null) {
            countQw.lambda().eq(Salesman::getVendorId, vendorId);
        }
        if (vendorCode != null && !vendorCode.trim().isEmpty()) {
            countQw.lambda().eq(Salesman::getVendorCode, vendorCode);
        }
        if (name != null && !name.trim().isEmpty()) {
            countQw.lambda().like(Salesman::getName, name);
        }
        if (status != null) {
            countQw.lambda().eq(Salesman::getStatus, EnableStatus.fromCode(status));
        }
        long total = salesmanRepository.selectCount(countQw);

        // 补充装机商代码信息
        List<Map<String, Object>> resultList = new ArrayList<>();
        for (Salesman s : list) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", s.getId());
            item.put("vendorId", s.getVendorId());
            item.put("vendorCode", s.getVendorCode()); // 经销商自己的代码
            item.put("name", s.getName());
            item.put("phone", s.getPhone());
            item.put("commissionRate", s.getCommissionRate());
            item.put("status", s.getStatus() != null ? s.getStatus().getCode() : null);
            item.put("remark", s.getRemark());
            item.put("createdAt", s.getCreatedAt());
            item.put("updatedAt", s.getUpdatedAt());

            // 查询所属装机商的代码
            if (s.getVendorId() != null) {
                Vendor vendor = vendorRepository.selectById(s.getVendorId());
                if (vendor != null) {
                    item.put("installerCode", vendor.getVendorCode());
                    item.put("installerName", vendor.getVendorName());
                }
            }
            resultList.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", resultList);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    /**
     * 根据ID获取业务员
     */
    public Salesman getById(Long id) {
        return salesmanRepository.selectById(id);
    }

    /**
     * 创建业务员
     */
    @Transactional
    public Salesman create(Salesman salesman) {
        if (salesman.getVendorId() == null || salesman.getVendorCode() == null) {
            throw new RuntimeException("经销商信息不能为空");
        }
        if (salesman.getName() == null || salesman.getName().trim().isEmpty()) {
            throw new RuntimeException("业务员姓名不能为空");
        }
        if (salesman.getCommissionRate() == null) {
            throw new RuntimeException("佣金比例不能为空");
        }
        if (salesman.getStatus() == null) {
            salesman.setStatus(EnableStatus.ENABLED);
        }
        salesman.setCreatedAt(new Date());
        salesman.setUpdatedAt(new Date());
        salesmanRepository.insert(salesman);
        log.info("创建业务员: id={}, name={}, vendorCode={}", salesman.getId(), salesman.getName(), salesman.getVendorCode());
        return salesman;
    }

    /**
     * 更新业务员
     */
    @Transactional
    public Salesman update(Long id, Salesman salesman) {
        Salesman existing = salesmanRepository.selectById(id);
        if (existing == null) {
            throw new RuntimeException("业务员不存在");
        }
        salesman.setId(id);
        salesman.setUpdatedAt(new Date());
        // 不允许修改经销商归属
        salesman.setVendorId(null);
        salesman.setVendorCode(null);
        salesmanRepository.updateById(salesman);
        log.info("更新业务员: id={}", id);
        return salesmanRepository.selectById(id);
    }



    /**
     * 更新业务员状态
     */
    @Transactional
    public void updateStatus(Long id, Integer status) {
        Salesman salesman = salesmanRepository.selectById(id);
        if (salesman == null) {
            throw new RuntimeException("业务员不存在");
        }
        salesman.setStatus(EnableStatus.fromCode(status));
        salesman.setUpdatedAt(new Date());
        salesmanRepository.updateById(salesman);
        log.info("更新业务员状态: id={}, status={}", id, status);
    }
}
