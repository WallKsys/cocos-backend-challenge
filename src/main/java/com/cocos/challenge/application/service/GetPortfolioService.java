package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.GetPortfolioQuery;
import com.cocos.challenge.application.port.out.AccountReader;
import com.cocos.challenge.application.port.out.LedgerReader;
import com.cocos.challenge.application.port.out.MarketDataRepository;
import com.cocos.challenge.application.port.out.OpenPosition;
import com.cocos.challenge.application.port.out.PortfolioReader;
import com.cocos.challenge.domain.exception.NotFoundException;
import com.cocos.challenge.domain.model.CashBalance;
import com.cocos.challenge.domain.model.Fill;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.MarketQuote;
import com.cocos.challenge.domain.model.Portfolio;
import com.cocos.challenge.domain.model.Position;
import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.domain.service.AverageCostCalculator;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class GetPortfolioService implements GetPortfolioQuery {

    private final AccountReader accounts;
    private final LedgerReader ledger;
    private final PortfolioReader portfolios;
    private final MarketDataRepository marketData;

    public GetPortfolioService(AccountReader accounts, LedgerReader ledger, PortfolioReader portfolios,
                               MarketDataRepository marketData) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.portfolios = portfolios;
        this.marketData = marketData;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Portfolio get(UserId userId) {
        accounts.find(userId)
                .orElseThrow(() -> new NotFoundException("User " + userId.value() + " was not found."));
        CashBalance cash = ledger.cashBalance(userId);
        List<OpenPosition> openPositions = portfolios.openPositions(userId);
        if (openPositions.isEmpty()) {
            return Portfolio.from(cash, List.of());
        }

        Set<InstrumentId> ids = openPositions.stream().map(OpenPosition::id).collect(Collectors.toSet());
        Map<InstrumentId, List<Fill>> fills = portfolios.fills(userId, ids);
        Map<InstrumentId, MarketQuote> quotes = marketData.latestQuotes(ids);
        List<Position> positions = openPositions.stream()
                .map(open -> Position.from(open.id(), open.ticker(), open.name(), open.quantity(),
                        open.reservedQuantity(), Optional.ofNullable(quotes.get(open.id())),
                        AverageCostCalculator.averageCost(fills.getOrDefault(open.id(), List.of()))))
                .toList();
        return Portfolio.from(cash, positions);
    }
}
