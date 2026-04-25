package com.pura365.camera.util;

import com.pura365.camera.domain.CloudPlan;
import com.pura365.camera.domain.CloudSubscription;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 订阅套餐能力判断工具。
 * <p>
 * 目标是把“纯4G流量”“纯云存”“4G+云存组合套餐”区分开，
 * 避免不同权益在读取和续期时互相串用。
 */
public final class SubscriptionPlanUtils {

    public static final int CLOUD_STORAGE_DISABLED = 0;
    public static final int CLOUD_STORAGE_CONTINUOUS = 1;
    public static final int CLOUD_STORAGE_EVENT = 2;

    private SubscriptionPlanUtils() {
    }

    public static boolean grantsAnySubscription(String type, String planId, String planName) {
        return grantsTraffic(type, planId, planName) || grantsCloudStorage(type, planId, planName);
    }

    public static boolean grantsCloudStorage(CloudPlan plan) {
        if (plan == null) {
            return false;
        }
        return grantsCloudStorage(plan.getType(), plan.getPlanId(), plan.getName());
    }

    public static boolean grantsCloudStorage(CloudSubscription subscription, CloudPlan plan) {
        if (subscription == null) {
            return false;
        }
        if (CloudTrialUtils.isFreeTrialPlan(subscription)) {
            return true;
        }
        return grantsCloudStorage(
                plan != null ? plan.getType() : null,
                subscription.getPlanId(),
                subscription.getPlanName()
        );
    }

    public static int resolveCloudStorageMode(CloudPlan plan) {
        if (plan == null) {
            return CLOUD_STORAGE_DISABLED;
        }
        return resolveCloudStorageMode(plan.getType(), plan.getPlanId(), plan.getName());
    }

    public static int resolveCloudStorageMode(CloudSubscription subscription, CloudPlan plan) {
        if (subscription == null) {
            return CLOUD_STORAGE_DISABLED;
        }
        return resolveCloudStorageMode(
                plan != null ? plan.getType() : null,
                subscription.getPlanId(),
                subscription.getPlanName()
        );
    }

    public static boolean grantsCloudStorage(String type, String planId, String planName) {
        if (CloudTrialUtils.isFreeTrialPlan(planId, planName)) {
            return true;
        }

        String normalizedType = normalize(type);
        if (isDirectCloudType(normalizedType) || isTrafficCloudType(normalizedType)) {
            return true;
        }

        String normalizedPlanId = normalize(planId);
        if (isDirectCloudType(normalizedPlanId) || isTrafficCloudType(normalizedPlanId)) {
            return true;
        }

        String normalizedPlanName = normalize(planName);
        return isTrafficCloudType(normalizedPlanName) || containsCloudKeyword(normalizedPlanName);
    }

    public static int resolveCloudStorageMode(String type, String planId, String planName) {
        if (!grantsCloudStorage(type, planId, planName)) {
            return CLOUD_STORAGE_DISABLED;
        }
        if (CloudTrialUtils.isFreeTrialPlan(planId, planName)) {
            return CLOUD_STORAGE_EVENT;
        }

        String normalizedType = normalize(type);
        if ("motion".equals(normalizedType)) {
            return CLOUD_STORAGE_EVENT;
        }
        if ("fulltime".equals(normalizedType)) {
            return CLOUD_STORAGE_CONTINUOUS;
        }

        String normalizedPlanId = normalize(planId);
        if (containsMotionKeyword(normalizedPlanId)) {
            return CLOUD_STORAGE_EVENT;
        }
        if (containsFulltimeKeyword(normalizedPlanId)) {
            return CLOUD_STORAGE_CONTINUOUS;
        }

        String normalizedPlanName = normalize(planName);
        if (containsMotionKeyword(normalizedPlanName)) {
            return CLOUD_STORAGE_EVENT;
        }
        if (containsFulltimeKeyword(normalizedPlanName)) {
            return CLOUD_STORAGE_CONTINUOUS;
        }

        return CLOUD_STORAGE_DISABLED;
    }

    public static boolean grantsTraffic(CloudPlan plan) {
        if (plan == null) {
            return false;
        }
        return grantsTraffic(plan.getType(), plan.getPlanId(), plan.getName());
    }

    public static boolean grantsTraffic(CloudSubscription subscription, CloudPlan plan) {
        if (subscription == null) {
            return false;
        }
        return grantsTraffic(
                plan != null ? plan.getType() : null,
                subscription.getPlanId(),
                subscription.getPlanName()
        );
    }

    public static boolean grantsTraffic(String type, String planId, String planName) {
        String normalizedType = normalize(type);
        if (containsTrafficKeyword(normalizedType)) {
            return true;
        }

        String normalizedPlanId = normalize(planId);
        if (containsTrafficKeyword(normalizedPlanId)) {
            return true;
        }

        String normalizedPlanName = normalize(planName);
        return containsTrafficKeyword(normalizedPlanName);
    }

    private static boolean isDirectCloudType(String value) {
        return "cloud_storage".equals(value)
                || "motion".equals(value)
                || "fulltime".equals(value);
    }

    private static boolean isTrafficCloudType(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return "traffic_cloud".equals(value)
                || ((value.contains("traffic") || value.contains("4g")) && containsCloudKeyword(value));
    }

    private static boolean containsTrafficKeyword(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return "traffic".equals(value)
                || value.contains("traffic")
                || value.startsWith("4g")
                || value.contains("4g流量")
                || value.contains("4g data");
    }

    private static boolean containsCloudKeyword(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return value.contains("cloud") || value.contains("云存");
    }

    private static boolean containsMotionKeyword(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return "motion".equals(value)
                || value.contains("motion")
                || value.contains("event")
                || value.contains("动态")
                || value.contains("事件");
    }

    private static boolean containsFulltimeKeyword(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return "fulltime".equals(value)
                || value.contains("fulltime")
                || value.contains("continuous")
                || value.contains("全天")
                || value.contains("连续");
    }

    private static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
