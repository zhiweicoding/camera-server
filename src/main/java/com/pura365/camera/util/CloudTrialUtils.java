package com.pura365.camera.util;

import com.pura365.camera.domain.CloudSubscription;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 免费云存储试用兼容工具。
 * <p>
 * 历史 7 天免费云存使用过通用标识，现统一归一到“动态录像”语义。
 */
public final class CloudTrialUtils {

    public static final int FREE_TRIAL_STORAGE_DAYS = 7;

    public static final String LEGACY_FREE_TRIAL_PLAN_ID = "free-trial-7d";
    public static final String LEGACY_FREE_TRIAL_PLAN_NAME = "7天免费试用";

    public static final String MOTION_FREE_TRIAL_PLAN_ID = "motion-trial-7d";
    public static final String MOTION_FREE_TRIAL_PLAN_NAME = "7天免费动态录像";

    private static final List<String> FREE_TRIAL_PLAN_IDS = Collections.unmodifiableList(
            Arrays.asList(LEGACY_FREE_TRIAL_PLAN_ID, MOTION_FREE_TRIAL_PLAN_ID)
    );
    private static final List<String> FREE_TRIAL_PLAN_NAMES = Collections.unmodifiableList(
            Arrays.asList(LEGACY_FREE_TRIAL_PLAN_NAME, MOTION_FREE_TRIAL_PLAN_NAME)
    );

    private CloudTrialUtils() {
    }

    public static boolean isFreeTrialPlan(String planId, String planName) {
        return contains(FREE_TRIAL_PLAN_IDS, planId) || contains(FREE_TRIAL_PLAN_NAMES, planName);
    }

    public static boolean isFreeTrialPlan(CloudSubscription subscription) {
        if (subscription == null) {
            return false;
        }
        return isFreeTrialPlan(subscription.getPlanId(), subscription.getPlanName());
    }

    public static String normalizePlanId(String planId, String planName) {
        return isFreeTrialPlan(planId, planName) ? MOTION_FREE_TRIAL_PLAN_ID : planId;
    }

    public static String normalizePlanName(String planId, String planName) {
        return isFreeTrialPlan(planId, planName) ? MOTION_FREE_TRIAL_PLAN_NAME : planName;
    }

    public static List<String> freeTrialPlanIds() {
        return FREE_TRIAL_PLAN_IDS;
    }

    public static List<String> freeTrialPlanNames() {
        return FREE_TRIAL_PLAN_NAMES;
    }

    private static boolean contains(List<String> candidates, String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return candidates.contains(value.trim());
    }
}
