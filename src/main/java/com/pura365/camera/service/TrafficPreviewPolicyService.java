package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pura365.camera.domain.CloudPlan;
import com.pura365.camera.domain.CloudSubscription;
import com.pura365.camera.domain.Device;
import com.pura365.camera.domain.DeviceTrafficSim;
import com.pura365.camera.domain.ManufacturedDevice;
import com.pura365.camera.domain.UserDevice;
import com.pura365.camera.enums.DeviceOnlineStatus;
import com.pura365.camera.repository.CloudPlanRepository;
import com.pura365.camera.repository.CloudSubscriptionRepository;
import com.pura365.camera.repository.DeviceRepository;
import com.pura365.camera.repository.DeviceTrafficSimRepository;
import com.pura365.camera.repository.ManufacturedDeviceRepository;
import com.pura365.camera.repository.UserDeviceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluate whether current user can preview a device under 4G traffic rules.
 */
@Service
public class TrafficPreviewPolicyService {

    private static final int TRIAL_DAYS = 3;
    private static final double WARN_THRESHOLD = 80.0;
    private static final double BLOCK_THRESHOLD = 100.0;
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(-?\\d+(?:\\.\\d+)?)");
    private static final long BYTES_PER_GB = 1024L * 1024L * 1024L;

    @Autowired
    private UserDeviceRepository userDeviceRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DeviceTrafficSimRepository deviceTrafficSimRepository;

    @Autowired
    private CloudSubscriptionRepository cloudSubscriptionRepository;

    @Autowired
    private CloudPlanRepository cloudPlanRepository;

    @Autowired
    private ManufacturedDeviceRepository manufacturedDeviceRepository;

    @Autowired
    private LinksFieldTrafficService linksFieldTrafficService;

    public PolicyEvaluation evaluate(Long currentUserId, String deviceId) {
        if (currentUserId == null) {
            return PolicyEvaluation.error(401, "未登录");
        }
        if (!StringUtils.hasText(deviceId)) {
            return PolicyEvaluation.error(400, "device_id 不能为空");
        }
        if (!hasUserDevice(currentUserId, deviceId)) {
            return PolicyEvaluation.error(403, "无权查看该设备");
        }

        Device device = deviceRepository.selectById(deviceId);
        if (device == null) {
            return PolicyEvaluation.error(404, "设备不存在");
        }

        String simId = findSimId(deviceId);
        ManufacturedDevice manufacturedDevice = findManufacturedDevice(deviceId);
        boolean is4gDevice = is4GDevice(device, manufacturedDevice, simId);

        Map<String, Object> policy = createBasePolicy(deviceId, is4gDevice, simId);
        boolean isOnline = device.getStatus() == DeviceOnlineStatus.ONLINE;
        policy.put("is_online", isOnline);

        if (!is4gDevice) {
            allow(policy, "OK_NON_4G_DEVICE", "非4G设备，不受4G流量策略限制");
            return PolicyEvaluation.success(policy);
        }

        if (!isOnline) {
            deny(policy, "DEVICE_OFFLINE", "设备离线，暂时无法预览");
            return PolicyEvaluation.success(policy);
        }

        ActiveTrafficSubscription activeTraffic = findActiveTrafficSubscription(deviceId);
        policy.put("has_active_traffic_plan", activeTraffic != null);

        if (activeTraffic == null) {
            applyNoRechargePolicy(policy, device, manufacturedDevice);
            return PolicyEvaluation.success(policy);
        }

        applyRechargePolicy(policy, activeTraffic, simId);
        return PolicyEvaluation.success(policy);
    }

    private Map<String, Object> createBasePolicy(String deviceId, boolean is4gDevice, String simId) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("device_id", deviceId);
        policy.put("is_4g_device", is4gDevice);
        policy.put("sim_id", simId);
        policy.put("can_preview", true);
        policy.put("need_recharge", false);
        policy.put("limit_required", false);
        policy.put("reason_code", "UNKNOWN");
        policy.put("reason_message", "ok");
        policy.put("usage_percent", null);
        policy.put("traffic_quota_gb", null);
        policy.put("expire_at", null);
        policy.put("trial_days_left", null);
        policy.put("has_active_traffic_plan", false);
        return policy;
    }

    private void applyNoRechargePolicy(Map<String, Object> policy, Device device, ManufacturedDevice manufacturedDevice) {
        LocalDateTime activatedAt = resolveActivatedAt(device, manufacturedDevice);
        if (activatedAt == null) {
            allow(policy, "TRIAL_UNCHECKED", "未检测到激活时间，暂按可预览处理");
            policy.put("need_recharge", true);
            return;
        }

        policy.put("activated_at", formatIsoTime(toDate(activatedAt)));

        long elapsedHours = Duration.between(activatedAt, LocalDateTime.now()).toHours();
        long remainingHours = (TRIAL_DAYS * 24L) - elapsedHours;
        int trialDaysLeft = remainingHours <= 0 ? 0 : (int) Math.ceil(remainingHours / 24.0);
        policy.put("trial_days_left", trialDaysLeft);

        if (elapsedHours < TRIAL_DAYS * 24L) {
            allow(policy, "TRIAL_FREE_PREVIEW", "设备未充值流量，激活3天内可预览");
            policy.put("need_recharge", true);
            return;
        }

        deny(policy, "UNRECHARGED_TRIAL_EXPIRED", "未充值流量，不能观看视频，请充值");
        policy.put("need_recharge", true);
    }

    private void applyRechargePolicy(Map<String, Object> policy, ActiveTrafficSubscription activeTraffic, String simId) {
        CloudSubscription subscription = activeTraffic.subscription;
        CloudPlan plan = activeTraffic.plan;

        policy.put("expire_at", formatIsoTime(subscription.getExpireAt()));
        policy.put("need_recharge", false);

        if (plan != null && plan.getTrafficGb() != null && plan.getTrafficGb() > 0) {
            policy.put("traffic_quota_gb", plan.getTrafficGb());
        }

        if (!StringUtils.hasText(simId)) {
            allow(policy, "SUBSCRIPTION_ACTIVE_NO_SIM", "流量套餐已生效，设备未配置SIM-ID，暂按可预览处理");
            return;
        }

        Integer trafficQuotaGb = (Integer) policy.get("traffic_quota_gb");
        if (trafficQuotaGb == null || trafficQuotaGb <= 0) {
            allow(policy, "SUBSCRIPTION_ACTIVE_NO_QUOTA", "流量套餐已生效，可预览");
            return;
        }

        long quotaBytes = trafficQuotaGb.longValue() * BYTES_PER_GB;

        try {
            Map<String, Object> thirdResult = linksFieldTrafficService.queryRemainingData(simId);
            TrafficUsage usage = parseTrafficUsage(thirdResult, quotaBytes);
            if (usage.usagePercent != null) {
                policy.put("usage_percent", round2(usage.usagePercent));
            }

            if (usage.usagePercent == null) {
                allow(policy, "SUBSCRIPTION_ACTIVE", "流量套餐已生效，可预览");
                return;
            }

            if (usage.usagePercent >= BLOCK_THRESHOLD) {
                deny(policy, "TRAFFIC_EXHAUSTED", "当月流量已用完，不能预览视频");
                return;
            }
            if (usage.usagePercent >= WARN_THRESHOLD) {
                allow(policy, "TRAFFIC_NEAR_EXHAUSTION", "当月流量即将耗尽，预览会卡顿");
                policy.put("limit_required", true);
                return;
            }

            allow(policy, "SUBSCRIPTION_ACTIVE", "流量套餐已生效，可预览");
        } catch (Exception e) {
            allow(policy, "SUBSCRIPTION_ACTIVE_QUERY_FAILED", "流量套餐已生效，可预览（流量查询失败）");
            policy.put("traffic_query_error", e.getMessage());
        }
    }

    private TrafficUsage parseTrafficUsage(Map<String, Object> thirdResult, long quotaBytes) {
        Map<String, Object> flattened = new LinkedHashMap<>();
        flattenMap("", thirdResult, flattened);

        List<ValueWithKey> usedCandidates = extractCandidates(flattened, true, quotaBytes);
        List<ValueWithKey> remainingCandidates = extractCandidates(flattened, false, quotaBytes);

        Double usedBytes = usedCandidates.isEmpty() ? null : usedCandidates.get(0).valueInBytes;
        Double remainingBytes = remainingCandidates.isEmpty() ? null : remainingCandidates.get(0).valueInBytes;

        if (usedBytes == null && remainingBytes != null) {
            usedBytes = Math.max(0, quotaBytes - remainingBytes);
        }
        if (usedBytes == null) {
            return new TrafficUsage(null);
        }
        if (quotaBytes <= 0) {
            return new TrafficUsage(null);
        }
        double percent = (usedBytes / quotaBytes) * 100.0;
        if (percent < 0) {
            percent = 0;
        }
        return new TrafficUsage(percent);
    }

    private List<ValueWithKey> extractCandidates(Map<String, Object> flattened, boolean used, long quotaBytes) {
        List<ValueWithKey> candidates = new ArrayList<>();
        for (Map.Entry<String, Object> entry : flattened.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase(Locale.ROOT);
            if (!matchesTrafficKey(key, used)) {
                continue;
            }
            Double numeric = parseNumeric(entry.getValue());
            if (numeric == null) {
                continue;
            }
            double bytes = convertToBytes(numeric, key, entry.getValue());
            if (bytes < 0) {
                continue;
            }
            candidates.add(new ValueWithKey(key, bytes));
        }
        if (candidates.isEmpty()) {
            return candidates;
        }

        // Prefer reasonable values close to quota range first.
        Collections.sort(candidates, (a, b) -> {
            double da = distanceToQuota(a.valueInBytes, quotaBytes);
            double db = distanceToQuota(b.valueInBytes, quotaBytes);
            if (da == db) {
                return Double.compare(b.valueInBytes, a.valueInBytes);
            }
            return Double.compare(da, db);
        });
        return candidates;
    }

    private double distanceToQuota(double valueInBytes, long quotaBytes) {
        if (quotaBytes <= 0) {
            return Double.MAX_VALUE;
        }
        return Math.abs(valueInBytes - quotaBytes);
    }

    private boolean matchesTrafficKey(String key, boolean used) {
        boolean trafficContext = key.contains("flow") || key.contains("traffic")
                || key.contains("data") || key.contains("usage") || key.contains("month");
        if (!trafficContext) {
            return false;
        }
        if (used) {
            return key.contains("used") || key.contains("usage") || key.contains("consume");
        }
        return key.contains("remaining") || key.contains("left") || key.contains("surplus")
                || key.contains("balance");
    }

    private double convertToBytes(Double value, String key, Object raw) {
        String rawText = raw == null ? "" : String.valueOf(raw).toLowerCase(Locale.ROOT);
        String all = key + "|" + rawText;
        if (all.contains("tb")) {
            return value * 1024.0 * 1024.0 * 1024.0 * 1024.0;
        }
        if (all.contains("gb")) {
            return value * 1024.0 * 1024.0 * 1024.0;
        }
        if (all.contains("mb")) {
            return value * 1024.0 * 1024.0;
        }
        if (all.contains("kb")) {
            return value * 1024.0;
        }
        return value;
    }

    private Double parseNumeric(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number) {
            return ((Number) raw).doubleValue();
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return null;
        }
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Double.parseDouble(matcher.group(1));
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private void flattenMap(String prefix, Object current, Map<String, Object> out) {
        if (current == null) {
            return;
        }
        if (current instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) current;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey() == null ? "" : String.valueOf(entry.getKey());
                String nextPrefix = prefix.isEmpty() ? key : (prefix + "." + key);
                flattenMap(nextPrefix, entry.getValue(), out);
            }
            return;
        }
        out.put(prefix, current);
    }

    private ActiveTrafficSubscription findActiveTrafficSubscription(String deviceId) {
        QueryWrapper<CloudSubscription> subscriptionQuery = new QueryWrapper<>();
        subscriptionQuery.lambda()
                .eq(CloudSubscription::getDeviceId, deviceId)
                .orderByDesc(CloudSubscription::getExpireAt)
                .last("LIMIT 50");
        List<CloudSubscription> subscriptions = cloudSubscriptionRepository.selectList(subscriptionQuery);
        if (subscriptions == null || subscriptions.isEmpty()) {
            return null;
        }

        Date now = new Date();
        for (CloudSubscription subscription : subscriptions) {
            if (subscription == null) {
                continue;
            }
            Date expireAt = subscription.getExpireAt();
            if (expireAt != null && !expireAt.after(now)) {
                continue;
            }
            CloudPlan plan = findPlan(subscription.getPlanId());
            if (isTrafficPlan(plan, subscription)) {
                return new ActiveTrafficSubscription(subscription, plan);
            }
        }
        return null;
    }

    private boolean isTrafficPlan(CloudPlan plan, CloudSubscription subscription) {
        if (plan != null) {
            return isTrafficType(plan.getType()) || startsWith4G(plan.getName());
        }
        if (subscription != null) {
            return startsWith4G(subscription.getPlanName()) || isTrafficType(subscription.getPlanId());
        }
        return false;
    }

    private boolean isTrafficType(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "traffic".equals(normalized)
                || normalized.startsWith("4g")
                || normalized.contains("traffic");
    }

    private boolean startsWith4G(String value) {
        return StringUtils.hasText(value) && value.trim().toLowerCase(Locale.ROOT).startsWith("4g");
    }

    private CloudPlan findPlan(String planId) {
        if (!StringUtils.hasText(planId)) {
            return null;
        }
        QueryWrapper<CloudPlan> planQuery = new QueryWrapper<>();
        planQuery.lambda().eq(CloudPlan::getPlanId, planId).last("LIMIT 1");
        CloudPlan plan = cloudPlanRepository.selectOne(planQuery);
        if (plan != null) {
            return plan;
        }
        try {
            return cloudPlanRepository.selectById(Long.parseLong(planId));
        } catch (Exception ignore) {
            return null;
        }
    }

    private boolean is4GDevice(Device device, ManufacturedDevice manufacturedDevice, String simId) {
        if (device != null && StringUtils.hasText(device.getNetworkType())) {
            String networkType = device.getNetworkType().trim().toLowerCase(Locale.ROOT);
            if ("4g".equals(networkType) || networkType.startsWith("4g")) {
                return true;
            }
        }
        if (StringUtils.hasText(simId)) {
            return true;
        }
        if (manufacturedDevice != null && StringUtils.hasText(manufacturedDevice.getNetworkLens())) {
            String networkLens = manufacturedDevice.getNetworkLens().trim().toUpperCase(Locale.ROOT);
            return networkLens.startsWith("C");
        }
        return false;
    }

    private ManufacturedDevice findManufacturedDevice(String deviceId) {
        QueryWrapper<ManufacturedDevice> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(ManufacturedDevice::getDeviceId, deviceId).last("LIMIT 1");
        return manufacturedDeviceRepository.selectOne(queryWrapper);
    }

    private LocalDateTime resolveActivatedAt(Device device, ManufacturedDevice manufacturedDevice) {
        if (manufacturedDevice != null && manufacturedDevice.getActivatedAt() != null) {
            return toLocalDateTime(manufacturedDevice.getActivatedAt());
        }
        if (device != null && device.getCreatedAt() != null) {
            return device.getCreatedAt();
        }
        return null;
    }

    private void allow(Map<String, Object> policy, String code, String message) {
        policy.put("can_preview", true);
        policy.put("reason_code", code);
        policy.put("reason_message", message);
    }

    private void deny(Map<String, Object> policy, String code, String message) {
        policy.put("can_preview", false);
        policy.put("reason_code", code);
        policy.put("reason_message", message);
    }

    private boolean hasUserDevice(Long userId, String deviceId) {
        QueryWrapper<UserDevice> query = new QueryWrapper<>();
        query.lambda().eq(UserDevice::getUserId, userId)
                .eq(UserDevice::getDeviceId, deviceId);
        return userDeviceRepository.selectCount(query) > 0;
    }

    private String findSimId(String deviceId) {
        QueryWrapper<DeviceTrafficSim> query = new QueryWrapper<>();
        query.lambda().eq(DeviceTrafficSim::getDeviceId, deviceId);
        DeviceTrafficSim mapping = deviceTrafficSimRepository.selectOne(query);
        return mapping == null ? null : mapping.getSimId();
    }

    private String formatIsoTime(Date date) {
        if (date == null) {
            return null;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(date);
    }

    private LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private Date toDate(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    private Double round2(Double value) {
        if (value == null) {
            return null;
        }
        return Math.round(value * 100.0) / 100.0;
    }

    private static class TrafficUsage {
        private final Double usagePercent;

        private TrafficUsage(Double usagePercent) {
            this.usagePercent = usagePercent;
        }
    }

    private static class ValueWithKey {
        private final String key;
        private final double valueInBytes;

        private ValueWithKey(String key, double valueInBytes) {
            this.key = key;
            this.valueInBytes = valueInBytes;
        }
    }

    private static class ActiveTrafficSubscription {
        private final CloudSubscription subscription;
        private final CloudPlan plan;

        private ActiveTrafficSubscription(CloudSubscription subscription, CloudPlan plan) {
            this.subscription = subscription;
            this.plan = plan;
        }
    }

    public static class PolicyEvaluation {
        private final int httpStatus;
        private final String errorMessage;
        private final Map<String, Object> policy;

        private PolicyEvaluation(int httpStatus, String errorMessage, Map<String, Object> policy) {
            this.httpStatus = httpStatus;
            this.errorMessage = errorMessage;
            this.policy = policy;
        }

        public static PolicyEvaluation success(Map<String, Object> policy) {
            return new PolicyEvaluation(200, null, policy);
        }

        public static PolicyEvaluation error(int httpStatus, String errorMessage) {
            return new PolicyEvaluation(httpStatus, errorMessage, null);
        }

        public boolean isOk() {
            return httpStatus == 200;
        }

        public int getHttpStatus() {
            return httpStatus;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public Map<String, Object> getPolicy() {
            return policy;
        }
    }
}
