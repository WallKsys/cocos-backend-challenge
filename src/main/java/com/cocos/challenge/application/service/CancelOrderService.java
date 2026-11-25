package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.CancelOrderUseCase;
import com.cocos.challenge.application.port.in.CancellationResult;
import com.cocos.challenge.application.port.out.AccountLock;
import com.cocos.challenge.application.port.out.BusinessMetrics;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.domain.model.UserId;
import org.springframework.transaction.annotation.Transactional;

public class CancelOrderService implements CancelOrderUseCase {

    public CancelOrderService(AccountLock lock, OrderLedgerRepository ledger, BusinessMetrics metrics) {
    }

    @Override
    @Transactional
    public CancellationResult cancel(UserId userId, long orderId) {
        throw new UnsupportedOperationException("Cancellation is not implemented");
    }
}
