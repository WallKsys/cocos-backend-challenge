package com.cocos.challenge.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class PositionTest {

    @Test
    @Tag("CU-01.02")
    void calculatesReturnsAgainstAverageCostAndPreviousClose() {
        Position position = position("PAMP", 40, "925.85", "921.80", "930");
        assertThat(position.averageCost()).contains(Money.of("930.00"));
        assertThat(position.totalReturnPct()).contains(percentage("-0.45"));
        assertThat(position.dailyChangePct()).contains(percentage("0.44"));
    }

    @Test
    @Tag("CU-01.03")
    void valuesAndFlagsNegativeQuantity() {
        Position position = position("BMA", -10, "1502.80", "1520.25", "1540");
        assertThat(position.quantity()).isEqualTo(-10);
        assertThat(position.marketValue()).contains(Money.of("-15028.00"));
        assertThat(position.warnings()).containsExactly(new PortfolioWarning(PortfolioWarningCode.NEGATIVE_POSITION,
                "Inconsistent position: the quantity of BMA is negative (-10); "
                        + "the recorded orders sell more shares than were bought."));
    }

    @Test
    void calculatesReturnFromTheUnroundedAverage() {
        Position position = position("PAMP", 3, "10.00", "10.00", "10.004");
        assertThat(position.averageCost()).contains(Money.of("10.00"));
        assertThat(position.totalReturnPct()).contains(percentage("-0.04"));
    }

    private static Position position(String ticker, long quantity, String close, String previousClose, String average) {
        return Position.from(new InstrumentId(1), ticker, ticker, quantity, 0,
                Optional.of(new MarketQuote(Money.of(close), Money.of(previousClose))),
                Optional.of(new BigDecimal(average)));
    }

    private static Percentage percentage(String value) {
        return new Percentage(new BigDecimal(value));
    }
}
