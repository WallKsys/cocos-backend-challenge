package com.cocos.challenge.domain.model;

import java.time.Instant;

public record CashMovement(long id, UserId userId, OrderSide side, OrderStatus status, Money amount,
        Rejection rejection, Instant createdAt, String idempotencyKey, RequestFingerprint fingerprint) {
}
