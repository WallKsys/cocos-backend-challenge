package com.cocos.challenge.database;

import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 002 US3 scenarios 1 to 3: timestamps are UTC (DEC-18) and the search columns are normalized (CM-05).
 */
@IntegrationTest
class TimestampsAndSearchColumnsIT {

    @Autowired
    JdbcClient jdbcClient;

    @Test
    @Transactional
    void firstSeedOrderIsReadAsUtcRegardlessOfSessionTimeZone() {
        jdbcClient.sql("SET TIME ZONE 'America/Argentina/Buenos_Aires'").update();

        OffsetDateTime datetime = jdbcClient.sql("SELECT datetime FROM orders ORDER BY id LIMIT 1")
                .query((rs, rowNum) -> rs.getObject("datetime", OffsetDateTime.class))
                .single();

        assertThat(datetime.toInstant()).isEqualTo(Instant.parse("2023-07-12T12:11:20Z"));
    }

    @Test
    void partialNameSearchReturnsAccentsRemovedMatches() {
        List<String> tickers = jdbcClient.sql("""
                        SELECT ticker
                        FROM instruments
                        WHERE name_normalized LIKE '%rio%'
                        ORDER BY ticker
                        """)
                .query(String.class)
                .list();

        assertThat(tickers).containsExactly("BHIP", "MOLI", "OEST", "RICH");
    }

    @Test
    void pampTickerNormalizedIsLowerCase() {
        String normalized = jdbcClient.sql(
                        "SELECT ticker_normalized FROM instruments WHERE ticker = 'PAMP'")
                .query(String.class)
                .single();

        assertThat(normalized).isEqualTo("pamp");
    }
}
