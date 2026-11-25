package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.GetOrderQuery;
import com.cocos.challenge.application.port.out.AccountReader;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.domain.model.OrderRecord;
import com.cocos.challenge.domain.model.UserId;
import org.springframework.transaction.annotation.Transactional;

public class GetOrderService implements GetOrderQuery {

    public GetOrderService(AccountReader accounts, OrderLedgerRepository ledger) {
    }

    @Override
    @Transactional(readOnly = true)
    public OrderRecord get(UserId userId, long orderId) {
        throw new UnsupportedOperationException("Order detail query is pending implementation");
    }
}
