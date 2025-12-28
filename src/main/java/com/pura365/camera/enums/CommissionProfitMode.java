package com.pura365.camera.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 套餐分润模式枚举
 * 适用于: PlanCommission.profitMode
 */
@Getter
@AllArgsConstructor
public enum CommissionProfitMode {

    PROFIT("profit", "按营收利润分润"),
    REVENUE("revenue", "按营收分润");

    @EnumValue
    @JsonValue
    private final String code;
    private final String description;

    public static CommissionProfitMode fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (CommissionProfitMode mode : values()) {
            if (mode.code.equals(code)) {
                return mode;
            }
        }
        return null;
    }
}
