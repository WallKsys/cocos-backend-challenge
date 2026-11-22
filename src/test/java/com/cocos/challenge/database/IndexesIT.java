package com.cocos.challenge.database;

import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 002 US4 scenario 1: every index of data-model.md § Indexes exists with its exact name.
 */
@IntegrationTest
class IndexesIT {

    @Autowired
    JdbcClient jdbcClient;

    @Test
    void everyDocumentedIndexExists() {
        List<String> indexes = jdbcClient.sql(
                        "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'")
                .query(String.class)
                .list();

        assertThat(indexes).contains(
                "marketdata_instrument_date_key",
                "instruments_ticker_key",
                "instruments_ticker_normalized_key",
                "instruments_ticker_normalized_trgm_idx",
                "instruments_name_normalized_trgm_idx",
                "orders_user_idempotency_key",
                "orders_user_status_instrument_idx",
                "orders_user_new_idx",
                "orders_user_history_idx");
    }
}
