package com.cocos.challenge.infrastructure.config;

import com.cocos.challenge.application.port.out.AccountRepository;
import com.cocos.challenge.application.port.out.BusinessMetrics;
import com.cocos.challenge.application.port.out.CashMovementRepository;
import com.cocos.challenge.application.port.out.LedgerReader;
import com.cocos.challenge.application.service.CashMovementService;
import com.cocos.challenge.application.service.IdempotencyLookup;
import com.cocos.challenge.domain.service.CashMovementEvaluator;
import com.cocos.challenge.infrastructure.adapter.out.persistence.JdbcCashMovementRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Clock;

@Configuration
public class CashMovementConfiguration {

    @Bean
    CashMovementRepository cashMovementRepository(JdbcClient jdbc) {
        return new JdbcCashMovementRepository(jdbc);
    }

    @Bean
    CashMovementService cashMovementService(AccountRepository accountLock, CashMovementRepository movements,
            LedgerReader ledger, BusinessMetrics metrics, Clock clock, IdempotencyLookup idempotencyLookup) {
        return new CashMovementService(accountLock, movements, ledger, new CashMovementEvaluator(), metrics,
                clock, idempotencyLookup);
    }
}
