package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.CashMovementUseCase;
import com.cocos.challenge.application.port.out.BusinessMetrics;
import com.cocos.challenge.application.port.out.InstrumentRepository;
import com.cocos.challenge.application.port.out.LedgerReader;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.application.port.out.OrderLedgerRepository.NewCashMovement;
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

import java.util.OptionalLong;

public class CashMovementService implements CashMovementUseCase {

    private final IdempotentMutationGuard guard;
    private final OrderLedgerRepository movements;
    private final InstrumentRepository instruments;
    private final LedgerReader ledger;
    private final CashMovementEvaluator evaluator;
    private final BusinessMetrics metrics;

    public CashMovementService(IdempotentMutationGuard guard, OrderLedgerRepository movements,
            InstrumentRepository instruments, LedgerReader ledger, CashMovementEvaluator evaluator,
            BusinessMetrics metrics) {
        this.guard = guard;
        this.movements = movements;
        this.instruments = instruments;
        this.ledger = ledger;
        this.evaluator = evaluator;
        this.metrics = metrics;
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
        Account account = guard.lock(userId);

        CashMovementRequest request = command.request();
        String key = command.idempotencyKey();
        RequestFingerprint fingerprint = request.fingerprint();
        OptionalLong existing = guard.find(userId, key, fingerprint);
        if (existing.isPresent()) {
            return new CashMovementResult(movements.findCashMovement(existing.getAsLong()), true);
        }

        CashBalance cash = ledger.cashBalance(userId);
        CashMovementEvaluator.Evaluation evaluation = evaluator.evaluate(request, account, cash);
        Rejection rejection = evaluation.rejection();
        InstrumentId instrumentId = instruments.findCashInstrument()
                .orElseThrow(() -> new IllegalStateException("ARS cash instrument is missing."));
        CashMovement saved = movements.save(new NewCashMovement(userId, instrumentId, request.side(),
                evaluation.status(), request.amount(), rejection, guard.now(),
                key, fingerprint));
        metrics.orderProcessed(request.side(), OrderType.MARKET, evaluation.status(),
                rejection == null ? null : rejection.reason());
        return new CashMovementResult(saved, false);
    }
}
