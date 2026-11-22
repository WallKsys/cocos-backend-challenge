package com.cocos.challenge.database;

import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Spec 002 US2 scenarios 1, 2 and 6: the constraints added by V2 reject invalid rows.
 * Rows 1, 2, 3, 4 and 14 of plan.md § Constraint checks. Every change is rolled back.
 */
@IntegrationTest
@Transactional
class SchemaConstraintsIT {

    @Autowired
    JdbcClient jdbcClient;

    @Test
    void instrumentWithAnExistingTickerIsRejected() {
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> jdbcClient.sql("""
                                INSERT INTO instruments (ticker, name, type)
                                VALUES ('PAMP', 'Duplicate Pampa', 'ACCIONES')
                                """)
                        .update())
                .withMessageContaining("instruments_ticker_key");
    }

    @Test
    void orderWithUnknownSideIsRejected() {
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertOrder(1, "HOLD", "MARKET", "NEW"))
                .withMessageContaining("orders_side_check");
    }

    @Test
    void orderWithUnknownTypeIsRejected() {
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertOrder(1, "BUY", "STOP", "NEW"))
                .withMessageContaining("orders_type_check");
    }

    @Test
    void orderWithUnknownStatusIsRejected() {
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertOrder(1, "BUY", "MARKET", "PENDING"))
                .withMessageContaining("orders_status_check");
    }

    @Test
    void orderWithNegativeSizeIsRejected() {
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertOrder(-1, "BUY", "MARKET", "NEW"))
                .withMessageContaining("orders_size_not_negative_check");
    }

    private void insertOrder(int size, String side, String type, String status) {
        jdbcClient.sql("""
                        INSERT INTO orders (instrumentid, userid, size, price, side, status, type, datetime)
                        VALUES (47, 1, :size, 100.00, :side, :status, :type, CURRENT_TIMESTAMP)
                        """)
                .param("size", size)
                .param("side", side)
                .param("status", status)
                .param("type", type)
                .update();
    }
}
