package com.cocos.challenge.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocos.challenge.domain.model.Portfolio.PortfolioWarning;
import com.cocos.challenge.domain.model.Portfolio.PortfolioWarningCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class PortfolioTest {

    @Test
    @Tag("CU-01.01")
    void includesReservedCashAndSignedPositionValuesInTheSeedTotal() {
        Position pamp = priced(47, "PAMP", 40, "925.85", "921.80", "930");
        Position bma = priced(12, "BMA", -10, "1502.80", "1520.25", "1540");
        Position metr = priced(35, "METR", 500, "229.50", "232.00", "250");
        Position closed = priced(99, "AAA", 0, "100", "100", "100");
        CashBalance cash = new CashBalance(Money.of("753000"), Money.of("125500"));
        Portfolio portfolio = Portfolio.from(cash, List.of(pamp, metr, bma, closed));

        assertThat(portfolio.totalValue()).isEqualTo(Money.of("889756.00"));
        assertThat(portfolio.cash()).isEqualTo(cash);
        assertThat(portfolio.cash().available()).isEqualTo(Money.of("627500.00"));
        assertThat(portfolio.positions()).containsExactly(bma, metr, pamp);
        assertThat(portfolio.warnings()).isEmpty();
    }

    @Test
    @Tag("CU-01.06")
    void excludesUnpricedPositionsAndReportsTheirTickersInPositionOrder() {
        Position pgr = unpriced(1, "PGR");
        Position loma = unpriced(2, "LOMA");
        Portfolio portfolio = Portfolio.from(new CashBalance(Money.of("1000"), Money.ZERO), List.of(pgr, loma));

        assertThat(pgr.lastPrice()).isEmpty();
        assertThat(pgr.marketValue()).isEmpty();
        assertThat(pgr.totalReturnPct()).isEmpty();
        assertThat(pgr.dailyChangePct()).isEmpty();
        assertThat(pgr.averageCost()).contains(Money.of("50"));
        assertThat(pgr.warnings()).containsExactly(new PortfolioWarning(PortfolioWarningCode.MISSING_MARKET_DATA,
                "No market data is available for PGR; its price, value and returns are unknown."));
        assertThat(portfolio.totalValue()).isEqualTo(Money.of("1000.00"));
        assertThat(portfolio.warnings()).containsExactly(new PortfolioWarning(
                PortfolioWarningCode.TOTAL_EXCLUDES_UNPRICED,
                "The total value excludes positions without market data: LOMA, PGR."));
    }

    @Test
    @Tag("CU-01.07")
    void emptyPortfolioHasZeroValueAndNoWarnings() {
        CashBalance cash = new CashBalance(Money.ZERO, Money.ZERO);
        Portfolio portfolio = Portfolio.from(cash, List.of());
        assertThat(portfolio.totalValue()).isEqualTo(Money.ZERO);
        assertThat(portfolio.cash()).isEqualTo(cash);
        assertThat(portfolio.positions()).isEmpty();
        assertThat(portfolio.warnings()).isEmpty();
    }

    @Test
    void flagsNegativeCashWithTheExactBalance() {
        Portfolio portfolio = Portfolio.from(new CashBalance(Money.of("-1.20"), Money.ZERO), List.of());
        assertThat(portfolio.totalValue()).isEqualTo(Money.of("-1.20"));
        assertThat(portfolio.warnings()).containsExactly(new PortfolioWarning(PortfolioWarningCode.NEGATIVE_CASH,
                "Inconsistent account: the cash balance is negative (ARS -1.20)."));
    }

    private static Position priced(long id, String ticker, long quantity, String price, String previous, String cost) {
        return Position.from(new InstrumentId(id), ticker, ticker, quantity, 0,
                Optional.of(new MarketQuote(Money.of(price), Money.of(previous))), Optional.of(new BigDecimal(cost)));
    }

    private static Position unpriced(long id, String ticker) {
        return Position.from(new InstrumentId(id), ticker, ticker, 10, 0,
                Optional.empty(), Optional.of(new BigDecimal("50")));
    }
}
