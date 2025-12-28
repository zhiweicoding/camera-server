package com.pura365.camera.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户设备角色枚举
 * 适用于: UserDevice.role
 */
@Getter
@AllArgsConstructor
public enum UserDeviceRole {

    OWNER("owner", "所有者"),
    VIEWER("viewer", "查看者");

    @EnumValue
    @JsonValue
    private final String code;
    private final String description;

    public static UserDeviceRole fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (UserDeviceRole role : values()) {
            if (role.code.equals(code)) {
                return role;
            }
        }
        return null;
    }
}
