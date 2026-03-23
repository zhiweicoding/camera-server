package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.domain.CloudPlan;
import com.pura365.camera.domain.CloudSubscription;
import com.pura365.camera.domain.PaymentOrder;
import com.pura365.camera.enums.CloudPlanPeriod;
import com.pura365.camera.enums.EnableStatus;
import com.pura365.camera.repository.CloudPlanRepository;
import com.pura365.camera.repository.CloudSubscriptionRepository;
import com.pura365.camera.repository.PaymentOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;

/**
 * 云存储套餐管理服务
 */
@Service
public class CloudPlanService {

    private static final Logger log = LoggerFactory.getLogger(CloudPlanService.class);
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String PRODUCT_TYPE_CLOUD_STORAGE = "cloud_storage";
    private static final String LANG_ZH = "zh";
    private static final String LANG_EN = "en";
    private static final int MAX_DEVICE_MODEL_LENGTH = 255;
    private static final String DEVICE_MODEL_SEPARATOR = ",";
    private static final String DEVICE_MODEL_SPLIT_REGEX = "[,\\s，]+";

    @Autowired
    private CloudPlanRepository planRepository;

    @Autowired
    private CloudSubscriptionRepository cloudSubscriptionRepository;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    /**
     * 获取所有启用的套餐列表（按类型和排序号）
     */
    public List<CloudPlan> listActivePlans() {
        return listActivePlans(LANG_ZH);
    }

    /**
     * 获取所有启用的套餐列表（按语言过滤，带回退）
     */
    public List<CloudPlan> listActivePlans(String lang) {
        String normalizedLang = normalizeLanguage(lang);
        return queryActivePlans(null, normalizedLang, true);
    }

    /**
     * 按类型获取启用的套餐列表
     * @param type 类型: motion/fulltime/traffic
     */
    public List<CloudPlan> listActivePlansByType(String type) {
        return listActivePlansByType(type, LANG_ZH);
    }

    /**
     * 按类型获取启用的套餐列表（按语言过滤，带回退）
     * @param type 类型: motion/fulltime/traffic
     * @param lang 语言: zh/en
     */
    public List<CloudPlan> listActivePlansByType(String type, String lang) {
        String normalizedLang = normalizeLanguage(lang);
        return queryActivePlans(type, normalizedLang, true);
    }

    /**
     * 分页查询套餐（管理后台用）
     */
    public Map<String, Object> listPlans(Integer page, Integer size, String type, String name, Integer status) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        EnableStatus statusEnum = null;
        if (status != null) {
            statusEnum = EnableStatus.fromCode(status);
            if (statusEnum == null) {
                throw new RuntimeException("status 仅支持 0 或 1");
            }
        }

        QueryWrapper<CloudPlan> qw = new QueryWrapper<>();
        if (type != null && !type.trim().isEmpty()) {
            qw.lambda().eq(CloudPlan::getType, type);
        }
        if (name != null && !name.trim().isEmpty()) {
            qw.lambda().like(CloudPlan::getName, name);
        }
        if (statusEnum != null) {
            qw.lambda().eq(CloudPlan::getStatus, statusEnum);
        }
        qw.lambda().orderByAsc(CloudPlan::getType).orderByAsc(CloudPlan::getSortOrder);

        // 分页查询
        int offset = (safePage - 1) * safeSize;
        qw.last("LIMIT " + offset + ", " + safeSize);
        List<CloudPlan> list = planRepository.selectList(qw);

        // 查询总数
        QueryWrapper<CloudPlan> countQw = new QueryWrapper<>();
        if (type != null && !type.trim().isEmpty()) {
            countQw.lambda().eq(CloudPlan::getType, type);
        }
        if (name != null && !name.trim().isEmpty()) {
            countQw.lambda().like(CloudPlan::getName, name);
        }
        if (statusEnum != null) {
            countQw.lambda().eq(CloudPlan::getStatus, statusEnum);
        }
        long total = planRepository.selectCount(countQw);

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
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
        String normalizedDeviceModel = normalizeDeviceModel(plan.getDeviceModel());
        plan.setDeviceModel(normalizedDeviceModel);
        normalizeAppleProductId(plan);
        validateType(plan.getType());
        validateTypeModelAndTraffic(plan.getType(), normalizedDeviceModel, plan.getTrafficGb());
        validatePeriod(plan.getPeriod(), plan.getPeriodNum());
        validateNumericFields(plan);
        validateAppleAutoRenew(plan, null);
        // 检查planId是否重复
        if (getByPlanId(plan.getPlanId()) != null) {
            throw new RuntimeException("套餐ID已存在");
        }
        if (plan.getStatus() == null) {
            plan.setStatus(EnableStatus.ENABLED);
        } else if (EnableStatus.fromCode(plan.getStatus().getCode()) == null) {
            throw new RuntimeException("status 仅支持 0 或 1");
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
        String effectiveType = plan.getType() != null ? plan.getType() : existing.getType();
        String effectiveDeviceModel = normalizeDeviceModel(
                plan.getDeviceModel() != null ? plan.getDeviceModel() : existing.getDeviceModel()
        );
        if (plan.getDeviceModel() != null) {
            plan.setDeviceModel(effectiveDeviceModel);
        }
        normalizeAppleProductId(plan);
        Integer effectiveTrafficGb = plan.getTrafficGb() != null ? plan.getTrafficGb() : existing.getTrafficGb();

        validateType(effectiveType);
        validateTypeModelAndTraffic(effectiveType, effectiveDeviceModel, effectiveTrafficGb);
        if (plan.getPeriod() != null || plan.getPeriodNum() != null) {
            validatePeriod(plan.getPeriod(), plan.getPeriodNum());
        }
        if (plan.getStatus() != null && EnableStatus.fromCode(plan.getStatus().getCode()) == null) {
            throw new RuntimeException("status 仅支持 0 或 1");
        }
        validateNumericFields(plan);
        validateAppleAutoRenew(plan, existing);
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

        long subscriptionCount = cloudSubscriptionRepository.selectCount(
                new QueryWrapper<CloudSubscription>().lambda().eq(CloudSubscription::getPlanId, plan.getPlanId())
        );
        long orderCount = paymentOrderRepository.selectCount(
                new QueryWrapper<PaymentOrder>()
                        .eq("product_type", PRODUCT_TYPE_CLOUD_STORAGE)
                        .and(w -> w.eq("product_id", plan.getPlanId()).or().eq("product_id", String.valueOf(plan.getId())))
        );

        if (subscriptionCount > 0 || orderCount > 0) {
            throw new RuntimeException("套餐已关联订阅或订单，无法删除");
        }
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
        EnableStatus statusEnum = EnableStatus.fromCode(status);
        if (statusEnum == null) {
            throw new RuntimeException("status 仅支持 0 或 1");
        }
        plan.setStatus(statusEnum);
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
    private void validateType(String type) {
        if (type == null || type.trim().isEmpty()) {
            throw new RuntimeException("套餐类型不能为空");
        }
    }

    private void validateTypeModelAndTraffic(String type, String deviceModel, Integer trafficGb) {
        List<String> modelCodes = parseModelCodes(deviceModel);
        if (modelCodes.isEmpty()) {
            throw new RuntimeException("机型不能为空");
        }
        String normalizedDeviceModel = String.join(DEVICE_MODEL_SEPARATOR, modelCodes);
        if (normalizedDeviceModel.length() > MAX_DEVICE_MODEL_LENGTH) {
            throw new RuntimeException("deviceModel length cannot exceed " + MAX_DEVICE_MODEL_LENGTH);
        }
        if (isTrafficType(type)) {
            boolean hasNon4G = modelCodes.stream().anyMatch(code -> !is4GModelCode(code));
            if (hasNon4G) {
                throw new RuntimeException("4G流量套餐仅支持4G机型（C开头）");
            }
            if (trafficGb == null || trafficGb <= 0) {
                throw new RuntimeException("4G流量套餐必须填写流量(GB)");
            }
        }
    }

    private boolean isTrafficType(String type) {
        return type != null && "traffic".equalsIgnoreCase(type.trim());
    }

    private boolean is4GModelCode(String modelCode) {
        return modelCode != null && modelCode.trim().toUpperCase(Locale.ROOT).startsWith("C");
    }

    private String normalizeDeviceModel(String deviceModel) {
        List<String> modelCodes = parseModelCodes(deviceModel);
        return String.join(DEVICE_MODEL_SEPARATOR, modelCodes);
    }

    private List<String> parseModelCodes(String deviceModel) {
        if (deviceModel == null || deviceModel.trim().isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> modelCodes = new LinkedHashSet<>();
        String[] rawCodes = deviceModel.split(DEVICE_MODEL_SPLIT_REGEX);
        for (String rawCode : rawCodes) {
            if (rawCode == null) {
                continue;
            }
            String normalized = rawCode.trim().toUpperCase(Locale.ROOT);
            if (!normalized.isEmpty()) {
                modelCodes.add(normalized);
            }
        }
        return new ArrayList<>(modelCodes);
    }

    private void validatePeriod(String period, Integer periodNum) {
        if (period != null && !period.trim().isEmpty() && CloudPlanPeriod.fromCode(period) == null) {
            throw new RuntimeException("计费周期无效，仅支持 month/year");
        }
        if (periodNum != null && periodNum <= 0) {
            throw new RuntimeException("periodNum 必须大于 0");
        }
    }

    private void validateNumericFields(CloudPlan plan) {
        validateNonNegative("price", plan.getPrice());
        validateNonNegative("originalPrice", plan.getOriginalPrice());
        validateNonNegative("planCost", plan.getPlanCost());

        if (plan.getStorageDays() != null && plan.getStorageDays() < 0) {
            throw new RuntimeException("storageDays 不能小于 0");
        }
        if (plan.getTrafficGb() != null && plan.getTrafficGb() < 0) {
            throw new RuntimeException("trafficGb 不能小于 0");
        }
        if (plan.getAutoRenew() != null && plan.getAutoRenew() != 0 && plan.getAutoRenew() != 1) {
            throw new RuntimeException("autoRenew 仅支持 0 或 1");
        }
        if (plan.getSortOrder() != null && plan.getSortOrder() < 0) {
            throw new RuntimeException("sortOrder 不能小于 0");
        }
    }

    private void normalizeAppleProductId(CloudPlan plan) {
        if (plan == null || plan.getAppleProductId() == null) {
            return;
        }
        String normalized = plan.getAppleProductId().trim();
        plan.setAppleProductId(normalized.isEmpty() ? null : normalized);
    }

    private void validateAppleAutoRenew(CloudPlan incoming, CloudPlan existing) {
        Integer effectiveAutoRenew = incoming.getAutoRenew() != null
                ? incoming.getAutoRenew()
                : existing != null ? existing.getAutoRenew() : null;
        String effectiveAppleProductId = incoming.getAppleProductId() != null
                ? incoming.getAppleProductId()
                : existing != null ? existing.getAppleProductId() : null;

        if (effectiveAutoRenew != null && effectiveAutoRenew == 1 && !StringUtils.hasText(effectiveAppleProductId)) {
            throw new RuntimeException("开启自动续费时，必须填写 Apple Product ID");
        }

        if (StringUtils.hasText(effectiveAppleProductId)) {
            QueryWrapper<CloudPlan> wrapper = new QueryWrapper<>();
            wrapper.lambda().eq(CloudPlan::getAppleProductId, effectiveAppleProductId);
            CloudPlan duplicate = planRepository.selectOne(wrapper);
            if (duplicate != null && (existing == null || !duplicate.getId().equals(existing.getId()))) {
                throw new RuntimeException("Apple Product ID 已被其他套餐使用");
            }
        }
    }

    private void validateNonNegative(String field, BigDecimal value) {
        if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException(field + " 不能小于 0");
        }
    }

    public List<Map<String, String>> listTypes() {
        return listTypes(LANG_ZH);
    }

    /**
     * 获取套餐类型列表（按语言返回类型名称）
     */
    public List<Map<String, String>> listTypes(String lang) {
        String normalizedLang = normalizeLanguage(lang);
        LinkedHashMap<String, String> typeNameMap = createDefaultTypeNameMap(normalizedLang);

        QueryWrapper<CloudPlan> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("DISTINCT type")
                .isNotNull("type")
                .ne("type", "")
                .orderByAsc("type");
        List<Object> dbTypes = planRepository.selectObjs(queryWrapper);
        for (Object dbType : dbTypes) {
            String typeCode = dbType == null ? "" : dbType.toString().trim();
            if (typeCode.isEmpty()) {
                continue;
            }
            typeNameMap.putIfAbsent(typeCode, typeCode);
        }

        List<Map<String, String>> types = new ArrayList<>();
        for (Map.Entry<String, String> entry : typeNameMap.entrySet()) {
            types.add(createTypeMap(entry.getKey(), entry.getValue()));
        }
        return types;
    }

    private List<CloudPlan> queryActivePlans(String type, String lang, boolean filterByLang) {
        QueryWrapper<CloudPlan> qw = new QueryWrapper<>();
        qw.lambda().eq(CloudPlan::getStatus, EnableStatus.ENABLED);
        if (type != null && !type.trim().isEmpty()) {
            qw.lambda().eq(CloudPlan::getType, type);
        }
        if (filterByLang && lang != null && !lang.trim().isEmpty()) {
            qw.lambda().likeRight(CloudPlan::getLanguage, lang);
        }
        qw.lambda().orderByAsc(CloudPlan::getType).orderByAsc(CloudPlan::getSortOrder);
        return planRepository.selectList(qw);
    }

    private LinkedHashMap<String, String> createDefaultTypeNameMap(String lang) {
        LinkedHashMap<String, String> typeNameMap = new LinkedHashMap<>();
        if (LANG_ZH.equals(lang)) {
            typeNameMap.put("motion", "动态录像");
            typeNameMap.put("fulltime", "全天录像");
            typeNameMap.put("traffic", "4G流量");
        } else {
            typeNameMap.put("motion", "Motion Recording");
            typeNameMap.put("fulltime", "24/7 Recording");
            typeNameMap.put("traffic", "4G Data");
        }
        return typeNameMap;
    }

    private String normalizeLanguage(String lang) {
        if (lang == null || lang.trim().isEmpty()) {
            return LANG_ZH;
        }
        String lower = lang.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("zh") ? LANG_ZH : LANG_EN;
    }

    private Map<String, String> createTypeMap(String code, String name) {
        Map<String, String> map = new HashMap<>();
        map.put("code", code);
        map.put("name", name);
        return map;
    }
}
