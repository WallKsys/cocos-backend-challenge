package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.GetPortfolioQuery;
import com.cocos.challenge.application.port.in.PlaceOrderCommand;
import com.cocos.challenge.application.port.in.PlaceOrderUseCase;
import com.cocos.challenge.application.port.out.AccountReader;
import com.cocos.challenge.application.port.out.LedgerReader;
import com.cocos.challenge.application.port.out.MarketDataRepository;
import com.cocos.challenge.application.port.out.PortfolioReader;
import com.cocos.challenge.domain.model.CashBalance;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.domain.model.OrderRequest;
import com.cocos.challenge.domain.model.OrderStatus;
import com.cocos.challenge.domain.model.Percentage;
import com.cocos.challenge.domain.model.Portfolio;
import com.cocos.challenge.domain.model.Portfolio.PortfolioWarningCode;
import com.cocos.challenge.domain.model.Position;
import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@IntegrationTest
@Tag("integration")
class GetPortfolioServiceIT {

    private static final UserId SEED_USER = new UserId(1);
    private static final InstrumentId PAMP = new InstrumentId(47);

    @Autowired
    GetPortfolioQuery query;

    @Autowired
    PlaceOrderUseCase placeOrder;

    @Autowired
    JdbcClient jdbc;

    @MockitoSpyBean
    AccountReader accounts;

    @MockitoSpyBean
    LedgerReader ledger;

    @MockitoSpyBean
    PortfolioReader portfolios;

    @MockitoSpyBean
    MarketDataRepository marketData;

    @Test
    @Tag("CU-01.01")
    void valuesTheSeedPortfolioFromTheLedger() {
        Portfolio portfolio = query.get(SEED_USER);

        assertThat(portfolio.totalValue()).isEqualTo(Money.of("889756.00"));
        assertCash(portfolio, "753000.00", "125500.00", "627500.00");
        assertThat(portfolio.positions()).extracting(Position::ticker, Position::quantity, Position::reservedQuantity,
                        position -> position.lastPrice().orElseThrow(), position -> position.marketValue().orElseThrow(),
                        position -> position.averageCost().orElseThrow(), position -> position.totalReturnPct().orElseThrow(),
                        position -> position.dailyChangePct().orElseThrow())
                .containsExactly(
                        tuple("BMA", -10L, 0L, Money.of("1502.80"), Money.of("-15028.00"), Money.of("1540.00"),
                                pct("-2.42"), pct("-1.15")),
                        tuple("METR", 500L, 0L, Money.of("229.50"), Money.of("114750.00"), Money.of("250.00"),
                                pct("-8.20"), pct("-1.08")),
                        tuple("PAMP", 40L, 0L, Money.of("925.85"), Money.of("37034.00"), Money.of("930.00"),
                                pct("-0.45"), pct("0.44")));
        assertThat(portfolio.positions().getFirst().warnings()).singleElement()
                .satisfies(warning -> assertThat(warning.code()).isEqualTo(PortfolioWarningCode.NEGATIVE_POSITION));
    }

    @Test
    @Tag("CU-01.07")
    void returnsAnEmptyPortfolioForAUserWithoutOrders() {
        Portfolio portfolio = query.get(new UserId(2));

        assertThat(portfolio.totalValue()).isEqualTo(Money.ZERO);
        assertCash(portfolio, "0.00", "0.00", "0.00");
        assertThat(portfolio.positions()).isEmpty();
        assertThat(portfolio.warnings()).isEmpty();
    }

    @Test
    @Tag("CU-01.11")
    void reflectsACommittedMarketBuyImmediately() {
        String key = "portfolio-market-buy";
        OrderRequest request = OrderRequest.of(PAMP.value(), "BUY", "MARKET", 10, null, null);
        try {
            assertThat(placeOrder.place(new PlaceOrderCommand(SEED_USER, key, request)).order().status())
                    .isEqualTo(OrderStatus.FILLED);

            Portfolio portfolio = query.get(SEED_USER);
            assertCash(portfolio, "743741.50", "125500.00", "618241.50");
            assertThat(portfolio.positions()).filteredOn(position -> position.instrumentId().equals(PAMP))
                    .singleElement().satisfies(position -> {
                        assertThat(position.quantity()).isEqualTo(50);
                        assertThat(position.marketValue()).contains(Money.of("46292.50"));
                    });
        } finally {
            jdbc.sql("DELETE FROM orders WHERE userid = :userId AND idempotency_key = :key")
                    .param("userId", SEED_USER.value()).param("key", key).update();
        }
    }

    @Test
    void loadsAllPositionsWithOneCallToEachReaderQuery() {
        Portfolio portfolio = query.get(SEED_USER);
        assertThat(portfolio.positions()).hasSize(3);
        Set<InstrumentId> instruments = portfolio.positions().stream()
                .map(Position::instrumentId).collect(Collectors.toSet());

        verify(accounts).find(SEED_USER);
        verify(ledger).cashBalance(SEED_USER);
        verify(portfolios).openPositions(SEED_USER);
        verify(portfolios).fills(SEED_USER, instruments);
        verify(marketData).latestQuotes(instruments);
        verifyNoMoreInteractions(accounts, ledger, portfolios, marketData);
    }

    private static void assertCash(Portfolio portfolio, String total, String reserved, String available) {
        assertThat(portfolio.cash()).isEqualTo(new CashBalance(Money.of(total), Money.of(reserved)));
        assertThat(portfolio.cash().available()).isEqualTo(Money.of(available));
    }

    private static Percentage pct(String value) {
        return new Percentage(new BigDecimal(value));
    }
}
