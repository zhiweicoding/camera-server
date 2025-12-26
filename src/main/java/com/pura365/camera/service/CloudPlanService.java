package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.domain.CloudPlan;
import com.pura365.camera.repository.CloudPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 云存储套餐管理服务
 */
@Service
public class CloudPlanService {

    private static final Logger log = LoggerFactory.getLogger(CloudPlanService.class);

    @Autowired
    private CloudPlanRepository planRepository;

    /**
     * 获取所有启用的套餐列表（按类型和排序号）
     */
    public List<CloudPlan> listActivePlans() {
        QueryWrapper<CloudPlan> qw = new QueryWrapper<>();
        qw.lambda().eq(CloudPlan::getStatus, 1)
                .orderByAsc(CloudPlan::getType)
                .orderByAsc(CloudPlan::getSortOrder);
        return planRepository.selectList(qw);
    }

    /**
     * 按类型获取启用的套餐列表
     * @param type 类型: motion/fulltime/traffic
     */
    public List<CloudPlan> listActivePlansByType(String type) {
        QueryWrapper<CloudPlan> qw = new QueryWrapper<>();
        qw.lambda().eq(CloudPlan::getType, type)
                .eq(CloudPlan::getStatus, 1)
                .orderByAsc(CloudPlan::getSortOrder);
        return planRepository.selectList(qw);
    }

    /**
     * 分页查询套餐（管理后台用）
     */
    public Map<String, Object> listPlans(Integer page, Integer size, String type, String name, Integer status) {
        QueryWrapper<CloudPlan> qw = new QueryWrapper<>();
        if (type != null && !type.trim().isEmpty()) {
            qw.lambda().eq(CloudPlan::getType, type);
        }
        if (name != null && !name.trim().isEmpty()) {
            qw.lambda().like(CloudPlan::getName, name);
        }
        if (status != null) {
            qw.lambda().eq(CloudPlan::getStatus, status);
        }
        qw.lambda().orderByAsc(CloudPlan::getType).orderByAsc(CloudPlan::getSortOrder);

        // 分页查询
        int offset = (page - 1) * size;
        qw.last("LIMIT " + offset + ", " + size);
        List<CloudPlan> list = planRepository.selectList(qw);

        // 查询总数
        QueryWrapper<CloudPlan> countQw = new QueryWrapper<>();
        if (type != null && !type.trim().isEmpty()) {
            countQw.lambda().eq(CloudPlan::getType, type);
        }
        if (name != null && !name.trim().isEmpty()) {
            countQw.lambda().like(CloudPlan::getName, name);
        }
        if (status != null) {
            countQw.lambda().eq(CloudPlan::getStatus, status);
        }
        long total = planRepository.selectCount(countQw);

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    /**
     * 根据ID获取套餐
     */
    public CloudPlan getById(Long id) {
        return planRepository.selectById(id);
    }

    /**
     * 根据planId获取套餐
     */
    public CloudPlan getByPlanId(String planId) {
        QueryWrapper<CloudPlan> qw = new QueryWrapper<>();
        qw.lambda().eq(CloudPlan::getPlanId, planId);
        return planRepository.selectOne(qw);
    }

    /**
     * 创建套餐
     */
    @Transactional
    public CloudPlan create(CloudPlan plan) {
        if (plan.getPlanId() == null || plan.getPlanId().trim().isEmpty()) {
            throw new RuntimeException("套餐ID不能为空");
        }
        if (plan.getName() == null || plan.getName().trim().isEmpty()) {
            throw new RuntimeException("套餐名称不能为空");
        }
        if (plan.getPrice() == null) {
            throw new RuntimeException("套餐价格不能为空");
        }
        // 检查planId是否重复
        if (getByPlanId(plan.getPlanId()) != null) {
            throw new RuntimeException("套餐ID已存在");
        }
        if (plan.getStatus() == null) {
            plan.setStatus(1);
        }
        if (plan.getSortOrder() == null) {
            // 获取当前类型下最大排序号
            QueryWrapper<CloudPlan> qw = new QueryWrapper<>();
            if (plan.getType() != null) {
                qw.lambda().eq(CloudPlan::getType, plan.getType());
            }
            qw.lambda().orderByDesc(CloudPlan::getSortOrder).last("LIMIT 1");
            CloudPlan last = planRepository.selectOne(qw);
            plan.setSortOrder(last != null && last.getSortOrder() != null ? last.getSortOrder() + 1 : 1);
        }
        plan.setCreatedAt(new Date());
        plan.setUpdatedAt(new Date());
        planRepository.insert(plan);
        log.info("创建套餐: id={}, planId={}, name={}", plan.getId(), plan.getPlanId(), plan.getName());
        return plan;
    }

    /**
     * 更新套餐
     */
    @Transactional
    public CloudPlan update(Long id, CloudPlan plan) {
        CloudPlan existing = planRepository.selectById(id);
        if (existing == null) {
            throw new RuntimeException("套餐不存在");
        }
        // 如果修改了planId，检查是否重复
        if (plan.getPlanId() != null && !plan.getPlanId().equals(existing.getPlanId())) {
            CloudPlan planIdExist = getByPlanId(plan.getPlanId());
            if (planIdExist != null && !planIdExist.getId().equals(id)) {
                throw new RuntimeException("套餐ID已被使用");
            }
        }
        plan.setId(id);
        plan.setUpdatedAt(new Date());
        planRepository.updateById(plan);
        log.info("更新套餐: id={}", id);
        return planRepository.selectById(id);
    }

    /**
     * 删除套餐
     */
    @Transactional
    public void delete(Long id) {
        CloudPlan plan = planRepository.selectById(id);
        if (plan == null) {
            throw new RuntimeException("套餐不存在");
        }
        // TODO: 检查是否有关联的订阅
        planRepository.deleteById(id);
        log.info("删除套餐: id={}, planId={}", id, plan.getPlanId());
    }

    /**
     * 更新套餐状态
     */
    @Transactional
    public void updateStatus(Long id, Integer status) {
        CloudPlan plan = planRepository.selectById(id);
        if (plan == null) {
            throw new RuntimeException("套餐不存在");
        }
        plan.setStatus(status);
        plan.setUpdatedAt(new Date());
        planRepository.updateById(plan);
        log.info("更新套餐状态: id={}, status={}", id, status);
    }

    /**
     * 批量更新排序
     */
    @Transactional
    public void updateSortOrder(List<Map<String, Object>> items) {
        for (Map<String, Object> item : items) {
            Long id = Long.valueOf(item.get("id").toString());
            Integer sortOrder = Integer.valueOf(item.get("sortOrder").toString());
            CloudPlan plan = new CloudPlan();
            plan.setId(id);
            plan.setSortOrder(sortOrder);
            plan.setUpdatedAt(new Date());
            planRepository.updateById(plan);
        }
        log.info("批量更新套餐排序: count={}", items.size());
    }

    /**
     * 获取套餐类型列表
     */
    public List<Map<String, String>> listTypes() {
        List<Map<String, String>> types = new ArrayList<>();
        types.add(createTypeMap("motion", "动态录像"));
        types.add(createTypeMap("fulltime", "全天录像"));
        types.add(createTypeMap("traffic", "4G流量"));
        return types;
    }

    private Map<String, String> createTypeMap(String code, String name) {
        Map<String, String> map = new HashMap<>();
        map.put("code", code);
        map.put("name", name);
        return map;
    }
}
