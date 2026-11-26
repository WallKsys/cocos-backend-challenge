package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.out.AccountRepository;
import com.cocos.challenge.application.port.out.BusinessMetrics;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.application.port.out.OrderLedgerRepository.IdempotencyRecord;
import com.cocos.challenge.domain.exception.IdempotencyConflictException;
import com.cocos.challenge.domain.exception.NotFoundException;
import com.cocos.challenge.domain.model.Account;
import com.cocos.challenge.domain.model.RequestFingerprint;
import com.cocos.challenge.domain.model.UserId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.OptionalLong;

/** Shared start of the operations that take an Idempotency-Key: placement, deposit and withdrawal (specs 005, 007). */
public final class IdempotentMutationGuard {

    private final AccountRepository accounts;
    private final OrderLedgerRepository orders;
    private final BusinessMetrics metrics;
    private final Clock clock;

    public IdempotentMutationGuard(AccountRepository accounts, OrderLedgerRepository orders, BusinessMetrics metrics,
            Clock clock) {
        this.accounts = accounts;
        this.orders = orders;
        this.metrics = metrics;
        this.clock = clock;
    }

    /** Locks the user row (TEC-16) and records the wait; an unknown user is "User <id> was not found.". */
    public Account lock(UserId userId) {
        long start = System.nanoTime();
        Account account = accounts.lock(userId)
                .orElseThrow(() -> new NotFoundException("User " + userId.value() + " was not found."));
        metrics.accountLockWaited(Duration.ofNanos(System.nanoTime() - start));
        return account;
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

    /** The current instant truncated to microseconds, the precision of PostgreSQL timestamps. */
    public Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
