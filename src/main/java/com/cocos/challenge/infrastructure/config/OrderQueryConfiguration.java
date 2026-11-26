package com.cocos.challenge.infrastructure.config;

import com.cocos.challenge.application.port.in.CancelOrderUseCase;
import com.cocos.challenge.application.port.in.OrderQuery;
import com.cocos.challenge.application.port.out.AccountRepository;
import com.cocos.challenge.application.port.out.BusinessMetrics;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.application.service.CancelOrderService;
import com.cocos.challenge.application.service.OrderQueryService;
import com.cocos.challenge.infrastructure.adapter.out.persistence.JdbcOrderLedgerRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration
public class OrderQueryConfiguration {

    @Bean
    JdbcOrderLedgerRepository orderLedgerRepository(JdbcClient jdbc) {
        return new JdbcOrderLedgerRepository(jdbc);
    }

    @Bean
    CancelOrderUseCase cancelOrderUseCase(AccountRepository lock, OrderLedgerRepository ledger, BusinessMetrics metrics) {
        return new CancelOrderService(lock, ledger, metrics);
    }

    @Bean
    OrderQuery orderQuery(AccountRepository accounts, OrderLedgerRepository ledger) {
        return new OrderQueryService(accounts, ledger);
    }
}
