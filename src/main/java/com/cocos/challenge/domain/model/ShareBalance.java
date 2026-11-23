package com.cocos.challenge.domain.model;

/**
 * Shares derived from FILLED orders, and shares reserved by NEW SELL orders (Constitution Principle I).
 */
public record ShareBalance(long quantity, long reserved) {

    public long available() {
        return quantity - reserved;
    }
}
