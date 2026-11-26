package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.out.BusinessMetrics;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.application.port.out.OrderLedgerRepository.IdempotencyRecord;
import com.cocos.challenge.domain.exception.IdempotencyConflictException;
import com.cocos.challenge.domain.model.RequestFingerprint;
import com.cocos.challenge.domain.model.UserId;

import java.util.Optional;
import java.util.OptionalLong;

public final class IdempotencyLookup {

    private final OrderLedgerRepository orders;
    private final BusinessMetrics metrics;

    public IdempotencyLookup(OrderLedgerRepository orders, BusinessMetrics metrics) {
        this.orders = orders;
        this.metrics = metrics;
    }

    public OptionalLong find(UserId userId, String key, RequestFingerprint fingerprint) {
        Optional<IdempotencyRecord> existing = orders.findIdempotencyRecord(userId, key);
        if (existing.isEmpty()) {
            return OptionalLong.empty();
        }
        IdempotencyRecord record = existing.get();
        if (!record.fingerprint().equals(fingerprint)) {
            metrics.idempotencyConflict();
            throw new IdempotencyConflictException(key);
        }
        metrics.idempotentReplay(record.side());
        return OptionalLong.of(record.orderId());
    }
}
