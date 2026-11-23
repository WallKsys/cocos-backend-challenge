package com.cocos.challenge.application.port.out;

import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.domain.model.OrderSide;
import com.cocos.challenge.domain.model.OrderStatus;
import com.cocos.challenge.domain.model.OrderType;
import com.cocos.challenge.domain.model.Rejection;
import com.cocos.challenge.domain.model.RequestFingerprint;
import com.cocos.challenge.domain.model.UserId;

import java.time.Instant;

public record NewOrder(
        UserId userId, InstrumentId instrumentId, String ticker, OrderSide side, OrderType type,
        OrderStatus status, long quantity, Money price, Money requestedAmount, Rejection rejection,
        Instant createdAt, String idempotencyKey, RequestFingerprint fingerprint) {
}
