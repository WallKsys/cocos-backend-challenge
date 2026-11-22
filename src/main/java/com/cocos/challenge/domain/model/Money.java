package com.cocos.challenge.domain.model;

import java.math.BigDecimal;

public record Money(BigDecimal amount) implements Comparable<Money> {

    public static final Money ZERO = null;

    public Money {
        throw new UnsupportedOperationException();
    }

    public static Money of(String amount) {
        throw new UnsupportedOperationException();
    }

    public static Money of(BigDecimal amount) {
        throw new UnsupportedOperationException();
    }

    public Money plus(Money other) {
        throw new UnsupportedOperationException();
    }

    public Money minus(Money other) {
        throw new UnsupportedOperationException();
    }

    public Money times(BigDecimal factor) {
        throw new UnsupportedOperationException();
    }

    public boolean isNegative() {
        throw new UnsupportedOperationException();
    }

    public boolean isGreaterThan(Money other) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int compareTo(Money other) {
        throw new UnsupportedOperationException();
    }
}
