package com.cocos.challenge.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

public record Order(
        long id, UserId userId, InstrumentId instrumentId, String ticker, OrderSide side, OrderType type,
        OrderStatus status, long quantity, Money price, Money requestedAmount, Rejection rejection,
        Instant createdAt, String idempotencyKey, RequestFingerprint fingerprint) {

    public Money totalAmount() {
        return price == null ? null : price.times(BigDecimal.valueOf(quantity));
    }

    public Money remainingAmount() {
        Money total = totalAmount();
        return requestedAmount == null || total == null ? null : requestedAmount.minus(total);
    }
}
