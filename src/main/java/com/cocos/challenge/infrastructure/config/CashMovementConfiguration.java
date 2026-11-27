package com.cocos.challenge.infrastructure.config;

import com.cocos.challenge.application.port.in.CashMovementUseCase;
import com.cocos.challenge.application.port.out.BusinessMetrics;
import com.cocos.challenge.application.port.out.InstrumentRepository;
import com.cocos.challenge.application.port.out.LedgerReader;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.application.service.CashMovementService;
import com.cocos.challenge.application.service.IdempotentMutationGuard;
import com.cocos.challenge.domain.service.CashMovementEvaluator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CashMovementConfiguration {

    @Bean
    CashMovementUseCase cashMovementUseCase(IdempotentMutationGuard guard, OrderLedgerRepository movements,
            InstrumentRepository instruments, LedgerReader ledger, BusinessMetrics metrics) {
        return new CashMovementService(guard, movements, instruments, ledger, new CashMovementEvaluator(),
                metrics);
    }
}
