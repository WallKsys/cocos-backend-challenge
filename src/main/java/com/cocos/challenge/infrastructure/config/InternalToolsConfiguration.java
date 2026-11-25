package com.cocos.challenge.infrastructure.config;

import com.cocos.challenge.application.port.in.DeleteOrderUseCase;
import com.cocos.challenge.application.port.in.ExecuteOrderUseCase;
import com.cocos.challenge.application.port.out.AccountLock;
import com.cocos.challenge.application.port.out.AdminOrderRepository;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.application.service.DeleteOrderService;
import com.cocos.challenge.application.service.ExecuteOrderService;
import com.cocos.challenge.infrastructure.adapter.out.persistence.JdbcAdminOrderRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration
public class InternalToolsConfiguration {

    @Bean
    AdminOrderRepository adminOrderRepository(JdbcClient jdbc) {
        return new JdbcAdminOrderRepository(jdbc);
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
}
