package com.cocos.challenge.infrastructure.adapter.in.rest.format;

import com.cocos.challenge.domain.model.Money;

import java.math.BigDecimal;

/** Decimal strings used by REST DTOs (TEC-20). */
public final class ApiDecimals {

    private ApiDecimals() {
    }

    public static String format(BigDecimal value) {
        throw new UnsupportedOperationException();
    }

    public static String format(Money value) {
        throw new UnsupportedOperationException();
    }

    public static BigDecimal parse(String text) {
        throw new UnsupportedOperationException();
    }
}
