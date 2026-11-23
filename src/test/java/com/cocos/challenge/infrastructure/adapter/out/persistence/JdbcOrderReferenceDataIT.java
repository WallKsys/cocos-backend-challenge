package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.domain.model.Instrument;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.InstrumentType;
import com.cocos.challenge.domain.model.MarketQuote;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class JdbcOrderReferenceDataIT {

    @Autowired
    JdbcClient jdbc;

    @Test
    void looksUpInstrumentsIncludingCurrency() {
        JdbcInstrumentRepository instruments = new JdbcInstrumentRepository(jdbc);
        InstrumentId currencyId = new InstrumentId(66);
        assertThat(instruments.findById(currencyId)).contains(new Instrument(currencyId, "ARS", InstrumentType.MONEDA));
        assertThat(instruments.findById(new InstrumentId(999))).isEmpty();
    }

    @Test
    void readsTheLatestQuoteAndHandlesInstrumentsWithoutQuotes() {
        JdbcMarketDataRepository marketData = new JdbcMarketDataRepository(jdbc);
        assertThat(marketData.latestQuote(new InstrumentId(47)))
                .contains(new MarketQuote(Money.of("925.85"), Money.of("921.80")));
        assertThat(marketData.latestQuote(new InstrumentId(3))).isEmpty();
    }
}
