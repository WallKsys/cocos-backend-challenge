package com.cocos.challenge.infrastructure.config;

import com.cocos.challenge.application.port.in.CashMovementUseCase;
import com.cocos.challenge.application.port.out.AccountRepository;
import com.cocos.challenge.application.port.out.BusinessMetrics;
import com.cocos.challenge.application.port.out.InstrumentRepository;
import com.cocos.challenge.application.port.out.LedgerReader;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.application.service.CashMovementService;
import com.cocos.challenge.application.service.IdempotencyLookup;
import com.cocos.challenge.domain.service.CashMovementEvaluator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class CashMovementConfiguration {

    @Bean
    CashMovementUseCase cashMovementUseCase(AccountRepository accountLock, OrderLedgerRepository movements,
            InstrumentRepository instruments, LedgerReader ledger, BusinessMetrics metrics, Clock clock,
            IdempotencyLookup idempotencyLookup) {
        return new CashMovementService(accountLock, movements, instruments, ledger, new CashMovementEvaluator(),
                metrics, clock, idempotencyLookup);
    }
}
