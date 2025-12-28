package com.pura365.camera.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 支付订单状态枚举
 * 适用于: PaymentOrder.status
 */
@Getter
@AllArgsConstructor
public enum PaymentOrderStatus {

    PENDING("pending", "待支付"),
    PAID("paid", "已支付"),
    CANCELLED("cancelled", "已取消"),
    REFUNDED("refunded", "已退款");

    @EnumValue
    @JsonValue
    private final String code;
    private final String description;

    public static PaymentOrderStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (PaymentOrderStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
