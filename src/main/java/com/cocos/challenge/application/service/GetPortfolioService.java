package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.GetPortfolioQuery;
import com.cocos.challenge.application.port.out.AccountReader;
import com.cocos.challenge.application.port.out.LedgerReader;
import com.cocos.challenge.application.port.out.MarketDataRepository;
import com.cocos.challenge.application.port.out.PortfolioReader;
import com.cocos.challenge.domain.model.Portfolio;
import com.cocos.challenge.domain.model.UserId;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

public class GetPortfolioService implements GetPortfolioQuery {

    public GetPortfolioService(AccountReader accounts, LedgerReader ledger, PortfolioReader portfolios,
                               MarketDataRepository marketData) {
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Portfolio get(UserId userId) {
        throw new UnsupportedOperationException("Portfolio query is not implemented");
    }
}
