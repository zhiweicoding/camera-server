package com.pura365.camera.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * SD卡状态枚举
 * 适用于: Device.sdState
 */
@Getter
@AllArgsConstructor
public enum SdCardStatus {

    NOT_PRESENT(0, "无SD卡"),
    PRESENT(1, "有SD卡");

    @EnumValue
    @JsonValue
    private final Integer code;
    private final String description;

    public static SdCardStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (SdCardStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
