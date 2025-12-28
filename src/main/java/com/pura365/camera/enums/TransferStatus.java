package com.pura365.camera.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 设备转让状态枚举
 */
public enum TransferStatus {
    
    PENDING("PENDING", "待确认"),
    COMPLETED("COMPLETED", "已完成"),
    CANCELLED("CANCELLED", "已取消");

    @EnumValue
    @JsonValue
    private final String code;
    
    private final String description;

    TransferStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static TransferStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (TransferStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return null;
    }
}
