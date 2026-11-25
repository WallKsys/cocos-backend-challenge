package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.GetOrderHistoryQuery;
import com.cocos.challenge.application.port.out.AccountReader;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.domain.model.OrderHistoryCriteria;
import com.cocos.challenge.domain.model.OrderHistoryPage;
import com.cocos.challenge.domain.model.UserId;
import org.springframework.transaction.annotation.Transactional;

public class GetOrderHistoryService implements GetOrderHistoryQuery {

    public GetOrderHistoryService(AccountReader accounts, OrderLedgerRepository ledger) {
    }

    @Override
    @Transactional(readOnly = true)
    public OrderHistoryPage list(UserId userId, OrderHistoryCriteria criteria) {
        throw new UnsupportedOperationException("Order history query is pending implementation");
    }
}
