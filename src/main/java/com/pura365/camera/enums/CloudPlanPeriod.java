package com.pura365.camera.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 云存储套餐周期枚举
 * 适用于: CloudPlan.period
 */
@Getter
@AllArgsConstructor
public enum CloudPlanPeriod {

    MONTH("month", "月付"),
    YEAR("year", "年付");

    @EnumValue
    @JsonValue
    private final String code;
    private final String description;

    public static CloudPlanPeriod fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (CloudPlanPeriod period : values()) {
            if (period.code.equals(code)) {
                return period;
            }
        }
        return null;
    }
}
