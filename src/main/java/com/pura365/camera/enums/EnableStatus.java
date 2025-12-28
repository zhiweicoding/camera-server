package com.pura365.camera.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通用启用/禁用状态枚举
 * 适用于: CloudPlan, Vendor, Salesman, SysDict, Assembler, PlanCommission 等
 */
@Getter
@AllArgsConstructor
public enum EnableStatus {

    DISABLED(0, "禁用"),
    ENABLED(1, "启用");

    @EnumValue
    @JsonValue
    private final Integer code;
    private final String description;

    public static EnableStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (EnableStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
