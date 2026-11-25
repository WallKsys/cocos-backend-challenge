package com.cocos.challenge.infrastructure.config;

import com.cocos.challenge.application.port.in.CreateUserUseCase;
import com.cocos.challenge.application.port.in.DeleteOrderUseCase;
import com.cocos.challenge.application.port.in.ExecuteOrderUseCase;
import com.cocos.challenge.application.port.in.RestoreSeedDataUseCase;
import com.cocos.challenge.application.port.in.UpdateMarketDataUseCase;
import com.cocos.challenge.application.port.out.AccountLock;
import com.cocos.challenge.application.port.out.AdminOrderRepository;
import com.cocos.challenge.application.port.out.InstrumentRepository;
import com.cocos.challenge.application.port.out.MarketDataWriter;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.application.port.out.SeedDataRestorer;
import com.cocos.challenge.application.port.out.UserWriter;
import com.cocos.challenge.application.service.CreateUserService;
import com.cocos.challenge.application.service.DeleteOrderService;
import com.cocos.challenge.application.service.ExecuteOrderService;
import com.cocos.challenge.application.service.RestoreSeedDataService;
import com.cocos.challenge.application.service.UpdateMarketDataService;
import com.cocos.challenge.infrastructure.adapter.out.persistence.JdbcAdminOrderRepository;
import com.cocos.challenge.infrastructure.adapter.out.persistence.JdbcMarketDataWriter;
import com.cocos.challenge.infrastructure.adapter.out.persistence.JdbcSeedDataRestorer;
import com.cocos.challenge.infrastructure.adapter.out.persistence.JdbcUserWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Clock;

@Configuration
public class InternalToolsConfiguration {

    @Bean
    AdminOrderRepository adminOrderRepository(JdbcClient jdbc) {
        return new JdbcAdminOrderRepository(jdbc);
    }

    @Bean
    MarketDataWriter marketDataWriter(JdbcClient jdbc) {
        return new JdbcMarketDataWriter(jdbc);
    }

    @Bean
    SeedDataRestorer seedDataRestorer(JdbcClient jdbc) {
        return new JdbcSeedDataRestorer(jdbc);
    }

    @Bean
    UserWriter userWriter(JdbcClient jdbc) {
        return new JdbcUserWriter(jdbc);
    }

    @Bean
    DeleteOrderUseCase deleteOrderUseCase(AccountLock lock, AdminOrderRepository adminOrders) {
        return new DeleteOrderService(lock, adminOrders);
    }

    @Bean
    ExecuteOrderUseCase executeOrderUseCase(AccountLock lock, AdminOrderRepository adminOrders,
            OrderLedgerRepository ledger) {
        return new ExecuteOrderService(lock, adminOrders, ledger);
    }

    @Bean
    UpdateMarketDataUseCase updateMarketDataUseCase(InstrumentRepository instruments,
            MarketDataWriter marketDataWriter, Clock clock) {
        return new UpdateMarketDataService(instruments, marketDataWriter, clock);
    }

    @Bean
    RestoreSeedDataUseCase restoreSeedDataUseCase(SeedDataRestorer seedDataRestorer) {
        return new RestoreSeedDataService(seedDataRestorer);
    }

    @Bean
    CreateUserUseCase createUserUseCase(UserWriter userWriter) {
        return new CreateUserService(userWriter);
    }
}
