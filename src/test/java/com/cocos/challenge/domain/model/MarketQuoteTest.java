package com.cocos.challenge.domain.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("CU-02.10")
@Tag("CU-02.11")
class MarketQuoteTest {

    @Test
    void calculatesPampDailyChangeRoundedToTwoDecimals() {
        MarketQuote quote = new MarketQuote(Money.of("925.85"), Money.of("921.80"));

        assertThat(quote.dailyChange()).contains(new Percentage(new java.math.BigDecimal("0.44")));
    }

    @Test
    void dailyChangeIsEmptyWithoutAPreviousClose() {
        MarketQuote quote = new MarketQuote(Money.of("925.85"), null);

        assertThat(quote.dailyChange()).isEmpty();
    }

    @Test
    void dailyChangeIsEmptyWhenPreviousCloseIsZero() {
        MarketQuote quote = new MarketQuote(Money.of("925.85"), Money.ZERO);

        assertThat(quote.dailyChange()).isEmpty();
    }
}
