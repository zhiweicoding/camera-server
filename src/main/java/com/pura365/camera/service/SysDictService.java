package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.domain.SysDict;
import com.pura365.camera.repository.SysDictRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 系统字典管理服务
 */
@Service
public class SysDictService {

    private static final Logger log = LoggerFactory.getLogger(SysDictService.class);

    @Autowired
    private SysDictRepository dictRepository;

    /**
     * 根据分类获取字典项列表（启用状态）
     * @param category 分类: network_lens/device_form/special_req/reserved/assembler_code
     * @return 字典项列表
     */
    public List<SysDict> listByCategory(String category) {
        QueryWrapper<SysDict> qw = new QueryWrapper<>();
        qw.lambda().eq(SysDict::getCategory, category)
                .eq(SysDict::getStatus, 1)
                .orderByAsc(SysDict::getSortOrder);
        return dictRepository.selectList(qw);
    }

    /**
     * 根据分类获取字典项列表（包含禁用）
     */
    public List<SysDict> listAllByCategory(String category) {
        QueryWrapper<SysDict> qw = new QueryWrapper<>();
        qw.lambda().eq(SysDict::getCategory, category)
                .orderByAsc(SysDict::getSortOrder);
        return dictRepository.selectList(qw);
    }

    /**
     * 获取所有分类
     */
    public List<String> listCategories() {
        return Arrays.asList("network_lens", "device_form", "special_req", "reserved", "assembler_code");
    }

    /**
     * 分页查询字典项
     */
    public Map<String, Object> listDicts(Integer page, Integer size, String category, String code, Integer status) {
        QueryWrapper<SysDict> qw = new QueryWrapper<>();
        if (category != null && !category.trim().isEmpty()) {
            qw.lambda().eq(SysDict::getCategory, category);
        }
        if (code != null && !code.trim().isEmpty()) {
            qw.lambda().like(SysDict::getCode, code);
        }
        if (status != null) {
            qw.lambda().eq(SysDict::getStatus, status);
        }
        qw.lambda().orderByAsc(SysDict::getCategory).orderByAsc(SysDict::getSortOrder);

        // 分页查询
        int offset = (page - 1) * size;
        qw.last("LIMIT " + offset + ", " + size);
        List<SysDict> list = dictRepository.selectList(qw);

        // 查询总数
        QueryWrapper<SysDict> countQw = new QueryWrapper<>();
        if (category != null && !category.trim().isEmpty()) {
            countQw.lambda().eq(SysDict::getCategory, category);
        }
        if (code != null && !code.trim().isEmpty()) {
            countQw.lambda().like(SysDict::getCode, code);
        }
        if (status != null) {
            countQw.lambda().eq(SysDict::getStatus, status);
        }
        long total = dictRepository.selectCount(countQw);

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    /**
     * 根据ID获取字典项
     */
    public SysDict getById(Long id) {
        return dictRepository.selectById(id);
    }

    /**
     * 根据分类和代码获取字典项
     */
    public SysDict getByCategoryAndCode(String category, String code) {
        QueryWrapper<SysDict> qw = new QueryWrapper<>();
        qw.lambda().eq(SysDict::getCategory, category)
                .eq(SysDict::getCode, code);
        return dictRepository.selectOne(qw);
    }

    /**
     * 创建字典项
     */
    @Transactional
    public SysDict create(SysDict dict) {
        if (dict.getCategory() == null || dict.getCategory().trim().isEmpty()) {
            throw new RuntimeException("字典分类不能为空");
        }
        if (dict.getCode() == null || dict.getCode().trim().isEmpty()) {
            throw new RuntimeException("字典代码不能为空");
        }
        // 检查同分类下代码是否重复
        SysDict existing = getByCategoryAndCode(dict.getCategory(), dict.getCode());
        if (existing != null) {
            throw new RuntimeException("该分类下代码已存在");
        }
        if (dict.getStatus() == null) {
            dict.setStatus(1);
        }
        if (dict.getSortOrder() == null) {
            // 获取当前分类下最大排序号
            QueryWrapper<SysDict> qw = new QueryWrapper<>();
            qw.lambda().eq(SysDict::getCategory, dict.getCategory())
                    .orderByDesc(SysDict::getSortOrder)
                    .last("LIMIT 1");
            SysDict last = dictRepository.selectOne(qw);
            dict.setSortOrder(last != null ? last.getSortOrder() + 1 : 1);
        }
        dict.setCreatedAt(new Date());
        dict.setUpdatedAt(new Date());
        dictRepository.insert(dict);
        log.info("创建字典项: id={}, category={}, code={}", dict.getId(), dict.getCategory(), dict.getCode());
        return dict;
    }

    /**
     * 更新字典项
     */
    @Transactional
    public SysDict update(Long id, SysDict dict) {
        SysDict existing = dictRepository.selectById(id);
        if (existing == null) {
            throw new RuntimeException("字典项不存在");
        }
        // 如果修改了代码，检查是否重复
        if (dict.getCode() != null && !dict.getCode().equals(existing.getCode())) {
            SysDict codeExist = getByCategoryAndCode(existing.getCategory(), dict.getCode());
            if (codeExist != null && !codeExist.getId().equals(id)) {
                throw new RuntimeException("该分类下代码已被使用");
            }
        }
        dict.setId(id);
        dict.setUpdatedAt(new Date());
        // 不允许修改分类
        dict.setCategory(null);
        dictRepository.updateById(dict);
        log.info("更新字典项: id={}", id);
        return dictRepository.selectById(id);
    }

    /**
     * 删除字典项
     */
    @Transactional
    public void delete(Long id) {
        SysDict dict = dictRepository.selectById(id);
        if (dict == null) {
            throw new RuntimeException("字典项不存在");
        }
        dictRepository.deleteById(id);
        log.info("删除字典项: id={}, category={}, code={}", id, dict.getCategory(), dict.getCode());
    }

    /**
     * 更新字典项状态
     */
    @Transactional
    public void updateStatus(Long id, Integer status) {
        SysDict dict = dictRepository.selectById(id);
        if (dict == null) {
            throw new RuntimeException("字典项不存在");
        }
        dict.setStatus(status);
        dict.setUpdatedAt(new Date());
        dictRepository.updateById(dict);
        log.info("更新字典项状态: id={}, status={}", id, status);
    }

    /**
     * 批量更新排序
     */
    @Transactional
    public void updateSortOrder(List<Map<String, Object>> items) {
        for (Map<String, Object> item : items) {
            Long id = Long.valueOf(item.get("id").toString());
            Integer sortOrder = Integer.valueOf(item.get("sortOrder").toString());
            SysDict dict = new SysDict();
            dict.setId(id);
            dict.setSortOrder(sortOrder);
            dict.setUpdatedAt(new Date());
            dictRepository.updateById(dict);
        }
        log.info("批量更新字典排序: count={}", items.size());
    }

    /**
     * 获取设备ID生成的选项（从字典表读取）
     * 用于替换 DeviceProductionService.getOptions() 中的硬编码
     */
    public Map<String, List<Map<String, String>>> getDeviceIdOptions() {
        Map<String, List<Map<String, String>>> options = new HashMap<>();

        // 网络+镜头配置
        options.put("network_lens", convertToOptionList(listByCategory("network_lens")));
        // 设备形态
        options.put("device_form", convertToOptionList(listByCategory("device_form")));
        // 特殊要求
        options.put("special_req", convertToOptionList(listByCategory("special_req")));
        // 预留位
        options.put("reserved", convertToOptionList(listByCategory("reserved")));
        // 装机商代码
        options.put("assembler_code", convertToOptionList(listByCategory("assembler_code")));

        return options;
    }

    private List<Map<String, String>> convertToOptionList(List<SysDict> dicts) {
        List<Map<String, String>> list = new ArrayList<>();
        for (SysDict dict : dicts) {
            Map<String, String> item = new HashMap<>();
            item.put("code", dict.getCode());
            item.put("name", dict.getName());
            list.add(item);
        }
        return list;
    }
}
