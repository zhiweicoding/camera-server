package com.pura365.camera.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 云存储套餐类型枚举
 * 适用于: CloudPlan.type
 */
@Getter
@AllArgsConstructor
public enum CloudPlanType {

    MOTION("motion", "动态录像"),
    FULLTIME("fulltime", "全天录像"),
    TRAFFIC("traffic", "4G流量");

    @EnumValue
    @JsonValue
    private final String code;
    private final String description;

    public static CloudPlanType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (CloudPlanType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
