package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.PlaceOrderCommand;
import com.cocos.challenge.application.port.in.PlaceOrderResult;
import com.cocos.challenge.application.port.in.PlaceOrderUseCase;
import com.cocos.challenge.application.port.out.AccountLock;
import com.cocos.challenge.application.port.out.BusinessMetrics;
import com.cocos.challenge.application.port.out.InstrumentRepository;
import com.cocos.challenge.application.port.out.LedgerReader;
import com.cocos.challenge.application.port.out.MarketDataRepository;
import com.cocos.challenge.application.port.out.OrderRepository;
import com.cocos.challenge.domain.service.OrderEvaluator;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

public class PlaceOrderService implements PlaceOrderUseCase {

    public PlaceOrderService(AccountLock accountLock, OrderRepository orders, InstrumentRepository instruments,
            MarketDataRepository marketData, LedgerReader ledger, OrderEvaluator evaluator,
            BusinessMetrics metrics, Clock clock) {
    }

    @Override
    @Transactional
    public PlaceOrderResult place(PlaceOrderCommand command) {
        throw new UnsupportedOperationException();
    }
}
