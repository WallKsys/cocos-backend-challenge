package com.cocos.challenge.database;

import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Spec 002 US2 scenarios 1 to 7: the constraints added by V2 and V3 reject invalid rows and accept valid
 * ones. Rows 1 to 14 of plan.md § Constraint checks. Every change is rolled back.
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
    void orderWithFractionalSharesIsRejected() {
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertOrder(new BigDecimal("1.5"), "BUY", "MARKET", "NEW"))
                .withMessageContaining("orders_whole_shares_check");
    }

    @Test
    void cashInWithCentsSucceeds() {
        assertThatNoException().isThrownBy(() -> insertOrder(
                66, 1, new BigDecimal("10.55"), BigDecimal.ONE, "CASH_IN", "MARKET", "FILLED"));
    }

    @Test
    void filledOrderWithRejectionCodeIsRejected() {
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertOrderWithRejection("FILLED", "INSUFFICIENT_FUNDS"))
                .withMessageContaining("orders_rejection_only_when_rejected_check");
    }

    @Test
    void rejectedOrderWithUnknownRejectionCodeIsRejected() {
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertOrderWithRejection("REJECTED", "SOMETHING_ELSE"))
                .withMessageContaining("orders_rejection_code_check");
    }

    @Test
    void priceOutOfRangeRejectionIsAccepted() {
        assertThatNoException().isThrownBy(() -> insertRejectedLimitOrder(
                47, 10, new BigDecimal("1111.03"), null, "PRICE_OUT_OF_RANGE"));
    }

    @Test
    void quantityTooLargeRejectionWithZeroSharesIsAccepted() {
        assertThatNoException().isThrownBy(() -> insertRejectedLimitOrder(
                3, 0, new BigDecimal("0.01"), new BigDecimal("9999999999.00"), "QUANTITY_TOO_LARGE"));
    }

    private void insertRejectedLimitOrder(
            int instrumentId, int quantity, BigDecimal price, BigDecimal requestedAmount, String reason) {
        jdbcClient.sql("""
                        INSERT INTO orders (instrumentid, userid, size, price, side, status, type, datetime,
                                            requested_amount, rejection_code, rejection_message)
                        VALUES (:instrumentId, 1, :quantity, :price, 'BUY', 'REJECTED', 'LIMIT',
                                CURRENT_TIMESTAMP, :requestedAmount, :reason, 'Rejected for the constraint test')
                        """)
                .param("instrumentId", instrumentId)
                .param("quantity", quantity)
                .param("price", price)
                .param("requestedAmount", requestedAmount)
                .param("reason", reason)
                .update();
    }

    @Test
    void orderWithIdempotencyKeyAndNoFingerprintIsRejected() {
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertOrderWithIdempotencyKeyOnly("k1"))
                .withMessageContaining("orders_idempotency_pair_check");
    }

    @Test
    void sameUserCannotReuseAnIdempotencyKey() {
        insertOrderWithIdempotencyKey(1, "k1");

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertOrderWithIdempotencyKey(1, "k1"))
                .withMessageContaining("orders_user_idempotency_key");
    }

    @Test
    void differentUsersMayReuseAnIdempotencyKey() {
        assertThatNoException().isThrownBy(() -> {
            insertOrderWithIdempotencyKey(1, "k1");
            insertOrderWithIdempotencyKey(2, "k1");
        });
    }

    @Test
    void ordersWithoutIdempotencyKeyAreNotCompared() {
        assertThatNoException().isThrownBy(() -> {
            insertOrder(47, 1, BigDecimal.ONE, new BigDecimal("100.00"), "BUY", "MARKET", "NEW");
            insertOrder(47, 1, BigDecimal.ONE, new BigDecimal("100.00"), "BUY", "MARKET", "NEW");
        });
    }

    @Test
    void largeCashInFitsTheMoneyColumns() {
        assertThatNoException().isThrownBy(() -> insertOrder(
                66, 1, new BigDecimal("99999999999.99"), BigDecimal.ONE, "CASH_IN", "MARKET", "FILLED"));
    }

    @Test
    void orderWithNegativeSizeIsRejected() {
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertOrder(-1, "BUY", "MARKET", "NEW"))
                .withMessageContaining("orders_size_not_negative_check");
    }

    private void insertOrder(int size, String side, String type, String status) {
        insertOrder(47, 1, new BigDecimal(size), new BigDecimal("100.00"), side, type, status);
    }

    private void insertOrder(BigDecimal size, String side, String type, String status) {
        insertOrder(47, 1, size, new BigDecimal("100.00"), side, type, status);
    }

    private void insertOrder(int instrumentId, int userId, BigDecimal size, BigDecimal price,
            String side, String type, String status) {
        jdbcClient.sql("""
                        INSERT INTO orders (instrumentid, userid, size, price, side, status, type, datetime)
                        VALUES (:instrumentId, :userId, :size, :price, :side, :status, :type, CURRENT_TIMESTAMP)
                        """)
                .param("instrumentId", instrumentId)
                .param("userId", userId)
                .param("size", size)
                .param("price", price)
                .param("side", side)
                .param("type", type)
                .param("status", status)
                .update();
    }

    private void insertOrderWithRejection(String status, String rejectionCode) {
        jdbcClient.sql("""
                        INSERT INTO orders (instrumentid, userid, size, price, side, status, type, datetime,
                                            rejection_code, rejection_message)
                        VALUES (47, 1, 1, 100.00, 'BUY', :status, 'MARKET', CURRENT_TIMESTAMP,
                                :rejectionCode, 'Rejected for the constraint test')
                        """)
                .param("status", status)
                .param("rejectionCode", rejectionCode)
                .update();
    }

    private void insertOrderWithIdempotencyKeyOnly(String key) {
        jdbcClient.sql("""
                        INSERT INTO orders (instrumentid, userid, size, price, side, status, type, datetime,
                                            idempotency_key)
                        VALUES (47, 1, 1, 100.00, 'BUY', 'NEW', 'MARKET', CURRENT_TIMESTAMP, :key)
                        """)
                .param("key", key)
                .update();
    }

    private void insertOrderWithIdempotencyKey(int userId, String key) {
        jdbcClient.sql("""
                        INSERT INTO orders (instrumentid, userid, size, price, side, status, type, datetime,
                                            idempotency_key, request_fingerprint)
                        VALUES (47, :userId, 1, 100.00, 'BUY', 'NEW', 'MARKET', CURRENT_TIMESTAMP,
                                :key, :fingerprint)
                        """)
                .param("userId", userId)
                .param("key", key)
                .param("fingerprint", "a".repeat(64))
                .update();
    }
}
