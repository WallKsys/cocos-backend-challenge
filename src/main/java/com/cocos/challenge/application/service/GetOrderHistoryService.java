package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.GetOrderHistoryQuery;
import com.cocos.challenge.application.port.out.AccountReader;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.domain.exception.NotFoundException;
import com.cocos.challenge.domain.model.HistoryCursor;
import com.cocos.challenge.domain.model.OrderHistoryCriteria;
import com.cocos.challenge.domain.model.OrderHistoryPage;
import com.cocos.challenge.domain.model.OrderRecord;
import com.cocos.challenge.domain.model.UserId;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public class GetOrderHistoryService implements GetOrderHistoryQuery {

    private final AccountReader accounts;
    private final OrderLedgerRepository ledger;

    public GetOrderHistoryService(AccountReader accounts, OrderLedgerRepository ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    @Override
    @Transactional(readOnly = true)
    public OrderHistoryPage list(UserId userId, OrderHistoryCriteria criteria) {
        accounts.find(userId)
                .orElseThrow(() -> new NotFoundException("User " + userId.value() + " was not found."));

        int limit = criteria.limit();
        List<OrderRecord> records = ledger.history(userId, criteria, limit + 1);
        if (records.size() <= limit) {
            return new OrderHistoryPage(records, null);
        }

        List<OrderRecord> items = records.subList(0, limit);
        OrderRecord last = items.getLast();
        return new OrderHistoryPage(items, new HistoryCursor(last.createdAt(), last.id()));
    }
}
