package com.cocos.challenge.infrastructure.config;

import com.cocos.challenge.application.port.in.GetPortfolioQuery;
import com.cocos.challenge.application.port.out.AccountRepository;
import com.cocos.challenge.application.port.out.LedgerReader;
import com.cocos.challenge.application.port.out.MarketDataRepository;
import com.cocos.challenge.application.service.GetPortfolioService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PortfolioConfiguration {

    @Bean
    GetPortfolioQuery getPortfolioQuery(AccountRepository accounts, LedgerReader ledger,
                                        MarketDataRepository marketData) {
        return new GetPortfolioService(accounts, ledger, marketData);
    }
}
