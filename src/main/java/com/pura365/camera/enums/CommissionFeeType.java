package com.pura365.camera.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 套餐分润手续费类型枚举
 * 适用于: PlanCommission.feeType
 */
@Getter
@AllArgsConstructor
public enum CommissionFeeType {

    FIXED("fixed", "固定比例"),
    MIXED("mixed", "混合（百分比+固定金额）");

    @EnumValue
    @JsonValue
    private final String code;
    private final String description;

    public static CommissionFeeType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (CommissionFeeType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
