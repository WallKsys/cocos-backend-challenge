package com.cocos.challenge.infrastructure.config;

import com.cocos.challenge.application.port.in.InternalToolsUseCase;
import com.cocos.challenge.application.port.out.AccountRepository;
import com.cocos.challenge.application.port.out.AdminOrderRepository;
import com.cocos.challenge.application.port.out.InstrumentRepository;
import com.cocos.challenge.application.port.out.MarketDataRepository;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.application.port.out.SeedDataRestorer;
import com.cocos.challenge.application.service.InternalToolsService;
import com.cocos.challenge.infrastructure.adapter.out.persistence.JdbcSeedDataRestorer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Clock;

@Configuration
public class InternalToolsConfiguration {

    @Bean
    SeedDataRestorer seedDataRestorer(JdbcClient jdbc) {
        return new JdbcSeedDataRestorer(jdbc);
    }

    @Bean
    InternalToolsUseCase internalToolsUseCase(AccountRepository accounts, AdminOrderRepository adminOrders,
            OrderLedgerRepository ledger, InstrumentRepository instruments, MarketDataRepository marketData,
            SeedDataRestorer seedDataRestorer, Clock clock) {
        return new InternalToolsService(
                accounts, adminOrders, ledger, instruments, marketData, seedDataRestorer, clock);
    }
}
