package com.pura365.camera.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 生产设备状态枚举
 * 适用于: ManufacturedDevice.status
 */
@Getter
@AllArgsConstructor
public enum ManufacturedDeviceStatus {

    MANUFACTURED("manufactured", "已生产"),
    ACTIVATED("activated", "已激活"),
    BOUND("bound", "已绑定"),
    DISABLED("disabled", "已禁用");

    @EnumValue
    @JsonValue
    private final String code;
    private final String description;

    public static ManufacturedDeviceStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ManufacturedDeviceStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
