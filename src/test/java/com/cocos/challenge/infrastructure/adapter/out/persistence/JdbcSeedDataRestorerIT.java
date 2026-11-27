package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.in.InternalToolsUseCase;
import com.cocos.challenge.domain.model.CashBalance;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.MarketQuote;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.domain.model.RequestFingerprint;
import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Tag("integration")
class JdbcSeedDataRestorerIT {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    InternalToolsUseCase restoreSeedData;

    @Test
    @Tag("CU-I.07")
    @Transactional
    void restoresSeedCountsLedgerPricesIdempotencyAndUtcTimestamps() {
        jdbc.sql("INSERT INTO users (email, accountnumber) VALUES ('extra@test.com', '10005')").update();
        jdbc.sql("UPDATE marketdata SET close = 1000.00 WHERE instrumentid = 47").update();
        jdbc.sql("""
                INSERT INTO orders (instrumentid, userid, size, price, side, status, type, datetime,
                                    idempotency_key, request_fingerprint)
                VALUES (47, 1, 10, 925.85, 'BUY', 'FILLED', 'MARKET', CURRENT_TIMESTAMP, 'k-restore', :fp)
                """).param("fp", RequestFingerprint.of("k-restore").hex()).update();
        jdbc.sql("SET LOCAL TIME ZONE 'America/Argentina/Buenos_Aires'").update();

        restoreSeedData.restoreSeedData();

        assertThat(jdbc.sql("SELECT COUNT(*) FROM users").query(Integer.class).single()).isEqualTo(4);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM instruments").query(Integer.class).single()).isEqualTo(66);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM marketdata").query(Integer.class).single()).isEqualTo(126);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM orders").query(Integer.class).single()).isEqualTo(11);
        CashBalance balance = new JdbcLedgerReader(jdbc).cashBalance(new UserId(1));
        assertThat(balance).isEqualTo(new CashBalance(Money.of("753000.00"), Money.of("125500.00")));
        assertThat(balance.available()).isEqualTo(Money.of("627500.00"));
        assertThat(new JdbcMarketDataRepository(jdbc).latestQuote(new InstrumentId(47)))
                .contains(new MarketQuote(Money.of("925.85"), Money.of("921.80")));
        assertThat(new JdbcOrderLedgerRepository(jdbc).findIdempotencyRecord(new UserId(1), "k-restore")).isEmpty();
        OffsetDateTime firstOrderTime = jdbc.sql("SELECT datetime FROM orders WHERE id = 1")
                .query((rs, rowNum) -> rs.getObject("datetime", OffsetDateTime.class)).single();
        assertThat(firstOrderTime.toInstant()).isEqualTo(Instant.parse("2023-07-12T12:11:20Z"));
    }
}
