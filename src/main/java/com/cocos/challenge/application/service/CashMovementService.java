package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.CashMovementCommand;
import com.cocos.challenge.application.port.in.CashMovementResult;
import com.cocos.challenge.application.port.in.DepositCashUseCase;
import com.cocos.challenge.application.port.in.WithdrawCashUseCase;
import com.cocos.challenge.application.port.out.AccountLock;
import com.cocos.challenge.application.port.out.BusinessMetrics;
import com.cocos.challenge.application.port.out.CashMovementRepository;
import com.cocos.challenge.application.port.out.LedgerReader;
import com.cocos.challenge.domain.service.CashMovementEvaluator;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

public class CashMovementService implements DepositCashUseCase, WithdrawCashUseCase {

    public CashMovementService(AccountLock accountLock, CashMovementRepository movements, LedgerReader ledger,
            CashMovementEvaluator evaluator, BusinessMetrics metrics, Clock clock, IdempotencyLookup idempotencyLookup) {
    }

    @Override
    @Transactional
    public CashMovementResult deposit(CashMovementCommand command) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Transactional
    public CashMovementResult withdraw(CashMovementCommand command) {
        throw new UnsupportedOperationException();
    }
}
