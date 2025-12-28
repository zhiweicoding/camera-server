package com.pura365.camera.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 生产批次状态枚举
 * 适用于: DeviceProductionBatch.status
 */
@Getter
@AllArgsConstructor
public enum ProductionBatchStatus {

    PENDING("pending", "待生产"),
    PRODUCING("producing", "生产中"),
    COMPLETED("completed", "已完成");

    @EnumValue
    @JsonValue
    private final String code;
    private final String description;

    public static ProductionBatchStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ProductionBatchStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
