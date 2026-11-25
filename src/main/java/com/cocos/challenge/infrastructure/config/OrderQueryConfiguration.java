package com.cocos.challenge.infrastructure.config;

import com.cocos.challenge.application.port.in.CancelOrderUseCase;
import com.cocos.challenge.application.port.in.GetOrderHistoryQuery;
import com.cocos.challenge.application.port.in.GetOrderQuery;
import com.cocos.challenge.application.port.out.AccountLock;
import com.cocos.challenge.application.port.out.AccountReader;
import com.cocos.challenge.application.port.out.BusinessMetrics;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.application.service.CancelOrderService;
import com.cocos.challenge.application.service.GetOrderHistoryService;
import com.cocos.challenge.application.service.GetOrderService;
import com.cocos.challenge.infrastructure.adapter.out.persistence.JdbcOrderLedgerRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration
public class OrderQueryConfiguration {

    @Bean
    OrderLedgerRepository orderLedgerRepository(JdbcClient jdbc) {
        return new JdbcOrderLedgerRepository(jdbc);
    }

    @Bean
    CancelOrderUseCase cancelOrderUseCase(AccountLock lock, OrderLedgerRepository ledger, BusinessMetrics metrics) {
        return new CancelOrderService(lock, ledger, metrics);
    }

    @Bean
    GetOrderQuery getOrderQuery(AccountReader accounts, OrderLedgerRepository ledger) {
        return new GetOrderService(accounts, ledger);
    }

    @Bean
    GetOrderHistoryQuery getOrderHistoryQuery(AccountReader accounts, OrderLedgerRepository ledger) {
        return new GetOrderHistoryService(accounts, ledger);
    }
}
