package com.pura365.camera.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 设备分享权限枚举
 * 适用于: DeviceShare.permission
 */
@Getter
@AllArgsConstructor
public enum DeviceSharePermission {

    VIEW_ONLY("view_only", "仅查看"),
    FULL_CONTROL("full_control", "完全控制");

    @EnumValue
    @JsonValue
    private final String code;
    private final String description;

    public static DeviceSharePermission fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (DeviceSharePermission permission : values()) {
            if (permission.code.equals(code)) {
                return permission;
            }
        }
        return null;
    }
}
