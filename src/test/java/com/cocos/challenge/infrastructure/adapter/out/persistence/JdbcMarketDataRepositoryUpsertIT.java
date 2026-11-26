package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.MarketDataRepository;
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
class JdbcMarketDataRepositoryUpsertIT {

    private static final LocalDate SEED_DATE = LocalDate.of(2023, 7, 14);

    private final JdbcMarketDataRepository writer;
    private final JdbcMarketDataRepository marketData;

    @Autowired
    JdbcMarketDataRepositoryUpsertIT(JdbcClient jdbc) {
        this.writer = new JdbcMarketDataRepository(jdbc);
        this.marketData = new JdbcMarketDataRepository(jdbc);
    }

    @Test
    @Tag("CU-I.06")
    @Transactional
    void updatesLatestRowPreservingPreviousCloseWhenNull() {
        InstrumentId pamp = new InstrumentId(47);

        var kept = writer.upsert(pamp, Money.of("1000.00"), null, LocalDate.of(2026, 11, 25));
        assertThat(kept).isEqualTo(new MarketDataRepository.StoredMarketData(SEED_DATE, Money.of("1000.00"), Money.of("921.80")));
        assertThat(marketData.latestQuote(pamp)).contains(new MarketQuote(Money.of("1000.00"), Money.of("921.80")));

        var updated = writer.upsert(pamp, Money.of("1050.00"), Money.of("990.00"), LocalDate.of(2026, 11, 25));
        assertThat(updated).isEqualTo(new MarketDataRepository.StoredMarketData(SEED_DATE, Money.of("1050.00"), Money.of("990.00")));
    }

    @Test
    @Tag("CU-I.06")
    @Transactional
    void insertsNewRowWhenInstrumentHasNoMarketDataThenUpdatesIt() {
        InstrumentId pgr = new InstrumentId(3);
        LocalDate today = LocalDate.of(2026, 11, 25);

        var inserted = writer.upsert(pgr, Money.of("50.00"), null, today);
        assertThat(inserted).isEqualTo(new MarketDataRepository.StoredMarketData(today, Money.of("50.00"), null));
        assertThat(marketData.latestQuote(pgr)).contains(new MarketQuote(Money.of("50.00"), null));

        var updated = writer.upsert(pgr, Money.of("55.00"), Money.of("50.00"), LocalDate.of(2026, 11, 26));
        assertThat(updated).isEqualTo(new MarketDataRepository.StoredMarketData(today, Money.of("55.00"), Money.of("50.00")));
    }
}
