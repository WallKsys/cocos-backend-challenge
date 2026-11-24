package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.OpenPosition;
import com.cocos.challenge.domain.model.Fill;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.MarketQuote;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.domain.model.OrderSide;
import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Tag("integration")
class JdbcPortfolioReaderIT {

    private static final InstrumentId PAMP = new InstrumentId(47);

    @Autowired
    JdbcClient jdbc;

    @Test
    @Tag("CU-01.04")
    void listsOnlyOpenPositionsInTickerOrder() {
        assertThat(new JdbcPortfolioReader(jdbc).openPositions(new UserId(1)))
                .extracting(OpenPosition::ticker).containsExactly("BMA", "METR", "PAMP");
    }

    @Test
    @Tag("CU-01.05")
    @Transactional
    void excludesAClosedPosition() {
        jdbc.sql("""
                INSERT INTO orders (instrumentid, userid, size, price, side, status, type, datetime)
                VALUES (47, 1, 40, 925.85, 'SELL', 'FILLED', 'MARKET', CURRENT_TIMESTAMP)
                """).update();
        assertThat(new JdbcPortfolioReader(jdbc).openPositions(new UserId(1)))
                .extracting(OpenPosition::ticker).containsExactly("BMA", "METR");
    }

    @Test
    @Tag("CU-01.14")
    @Transactional
    void ordersFillsByExecutionTimeAndThenId() {
        jdbc.sql("""
                INSERT INTO orders (instrumentid, userid, size, price, side, status, type, datetime)
                VALUES (47, 2, 5, 200, 'BUY', 'FILLED', 'MARKET', '2023-07-12T13:00:00Z'),
                       (47, 2, 10, 100, 'BUY', 'FILLED', 'MARKET', '2023-07-12T11:00:00Z'),
                       (47, 2, 10, 120, 'SELL', 'FILLED', 'MARKET', '2023-07-12T11:00:00Z')
                """).update();

        assertThat(new JdbcPortfolioReader(jdbc).fills(new UserId(2), Set.of(PAMP)).get(PAMP))
                .containsExactly(new Fill(OrderSide.BUY, 10, Money.of("100")),
                        new Fill(OrderSide.SELL, 10, Money.of("120")),
                        new Fill(OrderSide.BUY, 5, Money.of("200")));
    }

    @Test
    void readsLatestQuotesInOneBatchAndOmitsMissingQuotes() {
        InstrumentId metr = new InstrumentId(54);
        assertThat(new JdbcMarketDataRepository(jdbc).latestQuotes(Set.of(PAMP, metr, new InstrumentId(3))))
                .hasSize(2)
                .containsEntry(PAMP, new MarketQuote(Money.of("925.85"), Money.of("921.80")))
                .containsEntry(metr, new MarketQuote(Money.of("229.50"), Money.of("232.00")));
    }
}
