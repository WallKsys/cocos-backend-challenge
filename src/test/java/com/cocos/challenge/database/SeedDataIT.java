package com.cocos.challenge.database;

import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 002 US1 scenarios 1 and 4: Flyway applies V1 to V7 in order and the seed is loaded unchanged.
 */
@IntegrationTest
class SeedDataIT {

    @Autowired
    JdbcClient jdbcClient;

    @Test
    void flywayAppliedVersionsOneToSevenSuccessfully() {
        List<String> versions = jdbcClient.sql("""
                        SELECT version
                        FROM flyway_schema_history
                        WHERE version IS NOT NULL
                        ORDER BY installed_rank
                        """)
                .query(String.class)
                .list();
        List<Boolean> successes = jdbcClient.sql("""
                        SELECT success
                        FROM flyway_schema_history
                        WHERE version IS NOT NULL
                        ORDER BY installed_rank
                        """)
                .query(Boolean.class)
                .list();

        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7");
        assertThat(successes).containsExactly(true, true, true, true, true, true, true);
    }

    @Test
    void seedRowCountsMatchTheProvidedData() {
        assertThat(count("users")).isEqualTo(4);
        assertThat(count("instruments")).isEqualTo(66);
        assertThat(count("marketdata")).isEqualTo(126);
        assertThat(count("orders")).isEqualTo(11);
    }

    @Test
    void instrumentsWithoutMarketDataAndUsersWithoutOrders() {
        Integer instrumentsWithoutMarketData = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM instruments i
                        WHERE i.ticker IN ('PGR', 'IRCP', 'ARS')
                          AND NOT EXISTS (SELECT 1 FROM marketdata m WHERE m.instrumentid = i.id)
                        """)
                .query(Integer.class)
                .single();
        Integer ordersOfUsersTwoToFour = jdbcClient.sql(
                        "SELECT COUNT(*) FROM orders WHERE userid IN (2, 3, 4)")
                .query(Integer.class)
                .single();

        assertThat(instrumentsWithoutMarketData).isEqualTo(3);
        assertThat(ordersOfUsersTwoToFour).isZero();
    }

    private int count(String table) {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + table).query(Integer.class).single();
    }
}
