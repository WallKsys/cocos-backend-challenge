package com.cocos.challenge.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/** A trade or cash movement as stored in the orders ledger. */
public record OrderRecord(long id, InstrumentId instrumentId, String ticker, OrderSide side, OrderType type,
                          OrderStatus status, BigDecimal size, Money price, Money requestedAmount,
                          Rejection rejection, Instant createdAt) {

    public Long quantity() {
        throw new UnsupportedOperationException();
    }

    public Money amount() {
        throw new UnsupportedOperationException();
    }
}
