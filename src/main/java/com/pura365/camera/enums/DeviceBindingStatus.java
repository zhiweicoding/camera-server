package com.pura365.camera.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 设备绑定状态枚举
 * 适用于: DeviceBinding.status
 */
@Getter
@AllArgsConstructor
public enum DeviceBindingStatus {

    BINDING("binding", "绑定中"),
    SUCCESS("success", "绑定成功"),
    FAILED("failed", "绑定失败"),
    TIMEOUT("timeout", "绑定超时");

    @EnumValue
    @JsonValue
    private final String code;
    private final String description;

    public static DeviceBindingStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (DeviceBindingStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
