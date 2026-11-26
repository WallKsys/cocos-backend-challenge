package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.GetOrderQuery;
import com.cocos.challenge.application.port.out.AccountRepository;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.domain.exception.NotFoundException;
import com.cocos.challenge.domain.model.OrderRecord;
import com.cocos.challenge.domain.model.UserId;
import org.springframework.transaction.annotation.Transactional;

public class GetOrderService implements GetOrderQuery {

    private final AccountRepository accounts;
    private final OrderLedgerRepository ledger;

    public GetOrderService(AccountRepository accounts, OrderLedgerRepository ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    @Override
    @Transactional(readOnly = true)
    public OrderRecord get(UserId userId, long orderId) {
        accounts.find(userId)
                .orElseThrow(() -> new NotFoundException("User " + userId.value() + " was not found."));
        return ledger.findRecord(userId, orderId)
                .orElseThrow(() -> new NotFoundException("Order " + orderId + " was not found."));
    }
}
