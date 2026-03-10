package com.pura365.camera.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyScaleUtil {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private MoneyScaleUtil() {
    }

    public static BigDecimal keepTwoDecimals(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value.setScale(2, RoundingMode.DOWN);
    }

    public static BigDecimal percentOf(BigDecimal amount, BigDecimal rate) {
        if (amount == null || rate == null) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(rate).divide(HUNDRED, 2, RoundingMode.DOWN);
    }
}
