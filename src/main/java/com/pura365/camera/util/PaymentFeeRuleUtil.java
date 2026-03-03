package com.pura365.camera.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * 支付方式手续费规则工具。
 */
public final class PaymentFeeRuleUtil {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal WECHAT_RATE = new BigDecimal("1");
    private static final BigDecimal APPLE_RATE = new BigDecimal("30");
    private static final BigDecimal PAYPAL_RATE = new BigDecimal("4.4");
    private static final BigDecimal PAYPAL_FIXED = new BigDecimal("0.3");

    private PaymentFeeRuleUtil() {
    }

    public static boolean supportsMethod(String paymentMethod) {
        String normalized = normalizeMethod(paymentMethod);
        return "wechat".equals(normalized) || "apple".equals(normalized) || "paypal".equals(normalized);
    }

    public static BigDecimal calculateFee(BigDecimal amount, String paymentMethod) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        String normalized = normalizeMethod(paymentMethod);
        BigDecimal fee;
        switch (normalized) {
            case "wechat":
                fee = percentageFee(amount, WECHAT_RATE);
                break;
            case "apple":
                fee = percentageFee(amount, APPLE_RATE);
                break;
            case "paypal":
                fee = percentageFee(amount, PAYPAL_RATE).add(PAYPAL_FIXED);
                break;
            default:
                return BigDecimal.ZERO;
        }

        return fee.compareTo(BigDecimal.ZERO) > 0
                ? fee.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }

    public static String feeRateDesc(String paymentMethod) {
        String normalized = normalizeMethod(paymentMethod);
        switch (normalized) {
            case "wechat":
                return "1%";
            case "apple":
                return "30%";
            case "paypal":
                return "4.4% + 0.3USD";
            default:
                return "-";
        }
    }

    private static BigDecimal percentageFee(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private static String normalizeMethod(String paymentMethod) {
        if (paymentMethod == null) {
            return "";
        }

        String normalized = paymentMethod.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("wechat") || normalized.contains("wx")) {
            return "wechat";
        }
        if (normalized.contains("paypal")) {
            return "paypal";
        }
        if (normalized.contains("apple")) {
            return "apple";
        }
        return normalized;
    }
}
