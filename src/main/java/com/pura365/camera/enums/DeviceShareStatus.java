package com.pura365.camera.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 设备分享状态枚举
 * 适用于: DeviceShare.status
 */
@Getter
@AllArgsConstructor
public enum DeviceShareStatus {

    PENDING("pending", "待使用"),
    USED("used", "已使用"),
    EXPIRED("expired", "已过期"),
    REVOKED("revoked", "已撤销");

    @EnumValue
    @JsonValue
    private final String code;
    private final String description;

    public static DeviceShareStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (DeviceShareStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
