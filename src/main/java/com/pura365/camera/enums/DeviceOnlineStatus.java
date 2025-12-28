package com.pura365.camera.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 设备在线状态枚举
 * 适用于: Device.status
 */
@Getter
@AllArgsConstructor
public enum DeviceOnlineStatus {

    OFFLINE(0, "离线"),
    ONLINE(1, "在线");

    @EnumValue
    @JsonValue
    private final Integer code;
    private final String description;

    public static DeviceOnlineStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (DeviceOnlineStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 转换为字符串显示值
     */
    public String toDisplayValue() {
        return this == ONLINE ? "online" : "offline";
    }
}
