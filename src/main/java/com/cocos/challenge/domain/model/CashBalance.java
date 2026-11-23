package com.cocos.challenge.domain.model;

import java.util.Objects;

/**
 * Cash derived from FILLED orders, and cash reserved by NEW BUY orders (Constitution Principle I).
 */
public record CashBalance(Money cash, Money reserved) {

    public CashBalance {
        Objects.requireNonNull(cash, "cash");
        Objects.requireNonNull(reserved, "reserved");
    }

    public Money available() {
        return cash.minus(reserved);
    }
}
