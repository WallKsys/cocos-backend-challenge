package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.MarketDataWriter;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.MarketQuote;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Tag("integration")
class JdbcMarketDataWriterIT {

    private static final LocalDate SEED_DATE = LocalDate.of(2023, 7, 14);

    private final JdbcMarketDataWriter writer;
    private final JdbcMarketDataRepository marketData;

    @Autowired
    JdbcMarketDataWriterIT(JdbcClient jdbc) {
        this.writer = new JdbcMarketDataWriter(jdbc);
        this.marketData = new JdbcMarketDataRepository(jdbc);
    }

    @Test
    @Tag("CU-I.06")
    @Transactional
    void updatesLatestRowPreservingPreviousCloseWhenNull() {
        InstrumentId pamp = new InstrumentId(47);

        var kept = writer.upsert(pamp, Money.of("1000.00"), null, LocalDate.of(2026, 11, 25));
        assertThat(kept).isEqualTo(new MarketDataWriter.StoredMarketData(SEED_DATE, Money.of("1000.00"), Money.of("921.80")));
        assertThat(marketData.latestQuote(pamp)).contains(new MarketQuote(Money.of("1000.00"), Money.of("921.80")));

        var updated = writer.upsert(pamp, Money.of("1050.00"), Money.of("990.00"), LocalDate.of(2026, 11, 25));
        assertThat(updated).isEqualTo(new MarketDataWriter.StoredMarketData(SEED_DATE, Money.of("1050.00"), Money.of("990.00")));
    }

    @Test
    @Tag("CU-I.06")
    @Transactional
    void insertsNewRowWhenInstrumentHasNoMarketDataThenUpdatesIt() {
        InstrumentId pgr = new InstrumentId(3);
        LocalDate today = LocalDate.of(2026, 11, 25);

        var inserted = writer.upsert(pgr, Money.of("50.00"), null, today);
        assertThat(inserted).isEqualTo(new MarketDataWriter.StoredMarketData(today, Money.of("50.00"), null));
        assertThat(marketData.latestQuote(pgr)).contains(new MarketQuote(Money.of("50.00"), null));

        var updated = writer.upsert(pgr, Money.of("55.00"), Money.of("50.00"), LocalDate.of(2026, 11, 26));
        assertThat(updated).isEqualTo(new MarketDataWriter.StoredMarketData(today, Money.of("55.00"), Money.of("50.00")));
    }
}
