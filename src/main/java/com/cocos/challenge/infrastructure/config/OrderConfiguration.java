package com.cocos.challenge.infrastructure.config;

import com.cocos.challenge.application.port.in.PlaceOrderUseCase;
import com.cocos.challenge.application.port.out.AccountLock;
import com.cocos.challenge.application.port.out.BusinessMetrics;
import com.cocos.challenge.application.port.out.InstrumentRepository;
import com.cocos.challenge.application.port.out.LedgerReader;
import com.cocos.challenge.application.port.out.MarketDataRepository;
import com.cocos.challenge.application.port.out.OrderRepository;
import com.cocos.challenge.application.service.PlaceOrderService;
import com.cocos.challenge.domain.service.OrderEvaluator;
import com.cocos.challenge.infrastructure.adapter.out.persistence.JdbcAccountLock;
import com.cocos.challenge.infrastructure.adapter.out.persistence.JdbcInstrumentRepository;
import com.cocos.challenge.infrastructure.adapter.out.persistence.JdbcLedgerReader;
import com.cocos.challenge.infrastructure.adapter.out.persistence.JdbcMarketDataRepository;
import com.cocos.challenge.infrastructure.adapter.out.persistence.JdbcOrderRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Clock;

@Configuration
public class OrderConfiguration {

    @Bean
    AccountLock accountLock(JdbcClient jdbc) {
        return new JdbcAccountLock(jdbc);
    }

    @Bean
    OrderRepository orderRepository(JdbcClient jdbc) {
        return new JdbcOrderRepository(jdbc);
    }

    @Bean
    InstrumentRepository instrumentRepository(JdbcClient jdbc) {
        return new JdbcInstrumentRepository(jdbc);
    }

    @Bean
    MarketDataRepository marketDataRepository(JdbcClient jdbc) {
        return new JdbcMarketDataRepository(jdbc);
    }

    @Bean
    LedgerReader ledgerReader(JdbcClient jdbc) {
        return new JdbcLedgerReader(jdbc);
    }

    @Bean
    PlaceOrderUseCase placeOrderUseCase(AccountLock accountLock, OrderRepository orders,
            InstrumentRepository instruments, MarketDataRepository marketData, LedgerReader ledger,
            BusinessMetrics metrics, Clock clock) {
        return new PlaceOrderService(accountLock, orders, instruments, marketData, ledger,
                new OrderEvaluator(), metrics, clock);
    }
}
