package com.cocos.challenge.database;

import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 002 US1 scenarios 2 and 3: the five reference queries of data-model.md return the seed values.
 */
@IntegrationTest
class LedgerReferenceQueriesIT {

    private static final int USER_ID = 1;
    private static final int PAMP = 47;

    @Autowired
    JdbcClient jdbcClient;

    @Test
    void cashReservedCashAndAvailableCashMatchTheSeed() {
        BigDecimal cash = jdbcClient.sql("""
                        SELECT COALESCE(SUM(CASE
                                   WHEN side = 'CASH_IN'  THEN  size
                                   WHEN side = 'CASH_OUT' THEN -size
                                   WHEN side = 'BUY'      THEN -size * price
                                   WHEN side = 'SELL'     THEN  size * price
                               END), 0) AS cash
                        FROM orders
                        WHERE userid = :userId AND status = 'FILLED'
                        """)
                .param("userId", USER_ID)
                .query(BigDecimal.class)
                .single();
        BigDecimal reservedCash = jdbcClient.sql("""
                        SELECT COALESCE(SUM(size * price), 0) AS reserved_cash
                        FROM orders
                        WHERE userid = :userId AND status = 'NEW' AND side = 'BUY'
                        """)
                .param("userId", USER_ID)
                .query(BigDecimal.class)
                .single();

        assertThat(cash).isEqualByComparingTo("753000.00");
        assertThat(reservedCash).isEqualByComparingTo("125500.00");
        assertThat(cash.subtract(reservedCash)).isEqualByComparingTo("627500.00");
    }

    @Test
    void filledPositionsMatchTheSeed() {
        Map<Integer, BigDecimal> quantities = jdbcClient.sql("""
                        SELECT instrumentid,
                               SUM(CASE WHEN side = 'BUY' THEN size ELSE -size END) AS quantity
                        FROM orders
                        WHERE userid = :userId AND status = 'FILLED' AND side IN ('BUY', 'SELL')
                        GROUP BY instrumentid
                        """)
                .param("userId", USER_ID)
                .query((rs, rowNum) -> Map.entry(
                        rs.getInt("instrumentid"), rs.getBigDecimal("quantity")))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        assertThat(quantities).containsOnlyKeys(31, PAMP, 54);
        assertThat(quantities.get(PAMP)).isEqualByComparingTo("40");
        assertThat(quantities.get(54)).isEqualByComparingTo("500");
        assertThat(quantities.get(31)).isEqualByComparingTo("-10");
    }

    @Test
    void noSharesAreReservedByNewSellOrders() {
        List<Integer> reservedShares = jdbcClient.sql("""
                        SELECT instrumentid
                        FROM orders
                        WHERE userid = :userId AND status = 'NEW' AND side = 'SELL'
                        GROUP BY instrumentid
                        """)
                .param("userId", USER_ID)
                .query(Integer.class)
                .list();

        assertThat(reservedShares).isEmpty();
    }

    @Test
    void latestMarketDataOfPampMatchesTheSeed() {
        MarketData latest = jdbcClient.sql("""
                        SELECT close, previousclose, date
                        FROM marketdata
                        WHERE instrumentid = :instrumentId
                        ORDER BY date DESC
                        LIMIT 1
                        """)
                .param("instrumentId", PAMP)
                .query((rs, rowNum) -> new MarketData(
                        rs.getBigDecimal("close"),
                        rs.getBigDecimal("previousclose"),
                        rs.getObject("date", LocalDate.class)))
                .single();

        assertThat(latest.close()).isEqualByComparingTo("925.85");
        assertThat(latest.previousClose()).isEqualByComparingTo("921.80");
        assertThat(latest.date()).isEqualTo(LocalDate.of(2023, 7, 14));
    }

    private record MarketData(BigDecimal close, BigDecimal previousClose, LocalDate date) {
    }
}
