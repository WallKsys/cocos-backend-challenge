package com.cocos.challenge.infrastructure.config;

import com.cocos.challenge.application.port.in.GetPortfolioQuery;
import com.cocos.challenge.application.port.out.AccountReader;
import com.cocos.challenge.application.port.out.LedgerReader;
import com.cocos.challenge.application.port.out.MarketDataRepository;
import com.cocos.challenge.application.port.out.PortfolioReader;
import com.cocos.challenge.application.service.GetPortfolioService;
import com.cocos.challenge.infrastructure.adapter.out.persistence.JdbcAccountReader;
import com.cocos.challenge.infrastructure.adapter.out.persistence.JdbcPortfolioReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration
public class PortfolioConfiguration {

    @Bean
    AccountReader accountReader(JdbcClient jdbc) {
        return new JdbcAccountReader(jdbc);
    }

    @Bean
    PortfolioReader portfolioReader(JdbcClient jdbc) {
        return new JdbcPortfolioReader(jdbc);
    }

    @Bean
    GetPortfolioQuery getPortfolioQuery(AccountReader accounts, LedgerReader ledger, PortfolioReader portfolios,
                                        MarketDataRepository marketData) {
        return new GetPortfolioService(accounts, ledger, portfolios, marketData);
    }
}
