package com.cocos.challenge.application.port.out;

import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.domain.model.OrderSide;
import com.cocos.challenge.domain.model.OrderStatus;
import com.cocos.challenge.domain.model.Rejection;
import com.cocos.challenge.domain.model.RequestFingerprint;
import com.cocos.challenge.domain.model.UserId;

import java.time.Instant;

public record NewCashMovement(UserId userId, InstrumentId instrumentId, OrderSide side, OrderStatus status,
        Money amount, Rejection rejection, Instant createdAt, String idempotencyKey, RequestFingerprint fingerprint) {
}
