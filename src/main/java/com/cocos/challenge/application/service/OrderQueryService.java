package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.OrderQuery;
import com.cocos.challenge.application.port.out.AccountRepository;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.domain.exception.NotFoundException;
import com.cocos.challenge.domain.model.CursorPage;
import com.cocos.challenge.domain.model.HistoryCursor;
import com.cocos.challenge.domain.model.OrderHistoryCriteria;
import com.cocos.challenge.domain.model.OrderRecord;
import com.cocos.challenge.domain.model.UserId;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public class OrderQueryService implements OrderQuery {

    private final AccountRepository accounts;
    private final OrderLedgerRepository ledger;

    public OrderQueryService(AccountRepository accounts, OrderLedgerRepository ledger) {
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

    @Override
    @Transactional(readOnly = true)
    public CursorPage<OrderRecord, HistoryCursor> list(UserId userId, OrderHistoryCriteria criteria) {
        accounts.find(userId)
                .orElseThrow(() -> new NotFoundException("User " + userId.value() + " was not found."));

        int limit = criteria.limit();
        List<OrderRecord> records = ledger.history(userId, criteria, limit + 1);
        if (records.size() <= limit) {
            return new CursorPage<>(records, null);
        }

        List<OrderRecord> items = records.subList(0, limit);
        OrderRecord last = items.getLast();
        return new CursorPage<>(items, new HistoryCursor(last.createdAt(), last.id()));
    }
}
