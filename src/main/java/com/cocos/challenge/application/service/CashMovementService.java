package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.CashMovementCommand;
import com.cocos.challenge.application.port.in.CashMovementResult;
import com.cocos.challenge.application.port.in.DepositCashUseCase;
import com.cocos.challenge.application.port.in.WithdrawCashUseCase;
import com.cocos.challenge.application.port.out.AccountRepository;
import com.cocos.challenge.application.port.out.BusinessMetrics;
import com.cocos.challenge.application.port.out.InstrumentRepository;
import com.cocos.challenge.application.port.out.LedgerReader;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.application.port.out.OrderLedgerRepository.NewCashMovement;
import com.cocos.challenge.domain.exception.NotFoundException;
import com.cocos.challenge.domain.model.Account;
import com.cocos.challenge.domain.model.CashBalance;
import com.cocos.challenge.domain.model.CashMovement;
import com.cocos.challenge.domain.model.CashMovementRequest;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.OrderType;
import com.cocos.challenge.domain.model.Rejection;
import com.cocos.challenge.domain.model.RequestFingerprint;
import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.domain.service.CashMovementEvaluator;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.OptionalLong;

public class CashMovementService implements DepositCashUseCase, WithdrawCashUseCase {

    private final AccountRepository accountLock;
    private final OrderLedgerRepository movements;
    private final InstrumentRepository instruments;
    private final LedgerReader ledger;
    private final CashMovementEvaluator evaluator;
    private final BusinessMetrics metrics;
    private final Clock clock;
    private final IdempotencyLookup idempotencyLookup;

    public CashMovementService(AccountRepository accountLock, OrderLedgerRepository movements,
            InstrumentRepository instruments, LedgerReader ledger, CashMovementEvaluator evaluator,
            BusinessMetrics metrics, Clock clock, IdempotencyLookup idempotencyLookup) {
        this.accountLock = accountLock;
        this.movements = movements;
        this.instruments = instruments;
        this.ledger = ledger;
        this.evaluator = evaluator;
        this.metrics = metrics;
        this.clock = clock;
        this.idempotencyLookup = idempotencyLookup;
    }

    @Override
    @Transactional
    public CashMovementResult deposit(CashMovementCommand command) {
        return process(command);
    }

    @Override
    @Transactional
    public CashMovementResult withdraw(CashMovementCommand command) {
        return process(command);
    }

    private CashMovementResult process(CashMovementCommand command) {
        UserId userId = command.userId();
        long start = System.nanoTime();
        Account account = accountLock.lock(userId)
                .orElseThrow(() -> new NotFoundException("User " + userId.value() + " was not found."));
        metrics.accountLockWaited(Duration.ofNanos(System.nanoTime() - start));

        CashMovementRequest request = command.request();
        String key = command.idempotencyKey();
        RequestFingerprint fingerprint = request.fingerprint();
        OptionalLong existing = idempotencyLookup.find(userId, key, fingerprint);
        if (existing.isPresent()) {
            return new CashMovementResult(movements.findCashMovement(existing.getAsLong()), true);
        }

        CashBalance cash = ledger.cashBalance(userId);
        CashMovementEvaluator.Evaluation evaluation = evaluator.evaluate(request, account, cash);
        Rejection rejection = evaluation.rejection();
        InstrumentId instrumentId = instruments.findCashInstrument()
                .orElseThrow(() -> new IllegalStateException("ARS cash instrument is missing."));
        CashMovement saved = movements.save(new NewCashMovement(userId, instrumentId, request.side(),
                evaluation.status(), request.amount(), rejection, clock.instant().truncatedTo(ChronoUnit.MICROS),
                key, fingerprint));
        metrics.orderProcessed(request.side(), OrderType.MARKET, evaluation.status(),
                rejection == null ? null : rejection.reason());
        return new CashMovementResult(saved, false);
    }
}
