package com.cocos.challenge.infrastructure.adapter.in.rest.format;

import com.cocos.challenge.domain.model.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Decimal strings used by REST DTOs (TEC-20). */
public final class ApiDecimals {

    private ApiDecimals() {
    }

    public static String format(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public static String format(Money value) {
        return format(value.amount());
    }

    public static BigDecimal parse(String text) {
        return new BigDecimal(text);
    }
}
