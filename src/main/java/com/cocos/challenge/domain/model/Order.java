package com.cocos.challenge.domain.model;

import java.time.Instant;

public record Order(
        long id, UserId userId, InstrumentId instrumentId, String ticker, OrderSide side, OrderType type,
        OrderStatus status, long quantity, Money price, Money requestedAmount, Rejection rejection,
        Instant createdAt, String idempotencyKey, RequestFingerprint fingerprint) {

    public Money totalAmount() {
        throw new UnsupportedOperationException();
    }

    public Money remainingAmount() {
        throw new UnsupportedOperationException();
    }
}
