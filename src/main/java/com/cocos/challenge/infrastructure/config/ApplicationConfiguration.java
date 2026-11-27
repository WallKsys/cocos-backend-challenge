package com.cocos.challenge.infrastructure.config;

import com.cocos.challenge.application.port.in.CancelOrderUseCase;
import com.cocos.challenge.application.port.in.CashMovementUseCase;
import com.cocos.challenge.application.port.in.GetPortfolioQuery;
import com.cocos.challenge.application.port.in.OrderQuery;
import com.cocos.challenge.application.port.in.PlaceOrderUseCase;
import com.cocos.challenge.application.port.in.SearchInstrumentsQuery;
import com.cocos.challenge.application.port.out.AccountRepository;
import com.cocos.challenge.application.port.out.BusinessMetrics;
import com.cocos.challenge.application.port.out.InstrumentRepository;
import com.cocos.challenge.application.port.out.LedgerReader;
import com.cocos.challenge.application.port.out.MarketDataRepository;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.application.service.CancelOrderService;
import com.cocos.challenge.application.service.CashMovementService;
import com.cocos.challenge.application.service.GetPortfolioService;
import com.cocos.challenge.application.service.IdempotentMutationGuard;
import com.cocos.challenge.application.service.OrderQueryService;
import com.cocos.challenge.application.service.PlaceOrderService;
import com.cocos.challenge.application.service.SearchInstrumentsService;
import com.cocos.challenge.domain.service.CashMovementEvaluator;
import com.cocos.challenge.domain.service.OrderEvaluator;
import com.cocos.challenge.infrastructure.adapter.out.persistence.JdbcAccountRepository;
import com.cocos.challenge.infrastructure.adapter.out.persistence.JdbcInstrumentRepository;
import com.cocos.challenge.infrastructure.adapter.out.persistence.JdbcLedgerReader;
import com.cocos.challenge.infrastructure.adapter.out.persistence.JdbcMarketDataRepository;
import com.cocos.challenge.infrastructure.adapter.out.persistence.JdbcOrderLedgerRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Clock;

/** Core application beans: clock, repositories, guard and public use cases. */
@Configuration
public class ApplicationConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    AccountRepository accountRepository(JdbcClient jdbc) {
        return new JdbcAccountRepository(jdbc);
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
    JdbcOrderLedgerRepository orderLedgerRepository(JdbcClient jdbc) {
        return new JdbcOrderLedgerRepository(jdbc);
    }

    @Bean
    LedgerReader ledgerReader(JdbcClient jdbc) {
        return new JdbcLedgerReader(jdbc);
    }

    @Bean
    IdempotentMutationGuard idempotentMutationGuard(AccountRepository accounts, OrderLedgerRepository orders,
            BusinessMetrics metrics, Clock clock) {
        return new IdempotentMutationGuard(accounts, orders, metrics, clock);
    }

    @Bean
    PlaceOrderUseCase placeOrderUseCase(IdempotentMutationGuard guard, OrderLedgerRepository orders,
            InstrumentRepository instruments, MarketDataRepository marketData, LedgerReader ledger,
            BusinessMetrics metrics) {
        return new PlaceOrderService(guard, orders, instruments, marketData, ledger,
                new OrderEvaluator(), metrics);
    }

    @Bean
    CancelOrderUseCase cancelOrderUseCase(AccountRepository lock, OrderLedgerRepository ledger,
            BusinessMetrics metrics) {
        return new CancelOrderService(lock, ledger, metrics);
    }

    @Bean
    OrderQuery orderQuery(AccountRepository accounts, OrderLedgerRepository ledger) {
        return new OrderQueryService(accounts, ledger);
    }

    @Bean
    CashMovementUseCase cashMovementUseCase(IdempotentMutationGuard guard, OrderLedgerRepository movements,
            InstrumentRepository instruments, LedgerReader ledger, BusinessMetrics metrics) {
        return new CashMovementService(guard, movements, instruments, ledger, new CashMovementEvaluator(),
                metrics);
    }

    @Bean
    GetPortfolioQuery getPortfolioQuery(AccountRepository accounts, LedgerReader ledger,
            MarketDataRepository marketData) {
        return new GetPortfolioService(accounts, ledger, marketData);
    }

    @Bean
    SearchInstrumentsQuery searchInstrumentsQuery(InstrumentRepository repository) {
        return new SearchInstrumentsService(repository);
    }
}
