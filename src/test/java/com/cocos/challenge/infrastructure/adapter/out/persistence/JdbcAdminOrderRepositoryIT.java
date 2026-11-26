package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.domain.model.CashBalance;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.domain.model.OrderRecord;
import com.cocos.challenge.domain.model.OrderStatus;
import com.cocos.challenge.domain.model.RequestFingerprint;
import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Tag("integration")
class JdbcAdminOrderRepositoryIT {

    private static final UserId USER = new UserId(1);

    private final JdbcClient jdbc;
    private final JdbcAdminOrderRepository adminOrders;
    private final JdbcOrderLedgerRepository orderLedger;
    private final JdbcLedgerReader ledger;
    private final JdbcOrderLedgerRepository orderRepository;

    @Autowired
    JdbcAdminOrderRepositoryIT(JdbcClient jdbc) {
        this.jdbc = jdbc;
        this.adminOrders = new JdbcAdminOrderRepository(jdbc);
        this.orderLedger = new JdbcOrderLedgerRepository(jdbc);
        this.ledger = new JdbcLedgerReader(jdbc);
        this.orderRepository = new JdbcOrderLedgerRepository(jdbc);
    }

    @Test
    @Tag("CU-I.02")
    @Tag("CU-I.09")
    @Transactional
    void executesNewOrderOnceAndTurnsReservationIntoPosition() {
        assertThat(adminOrders.findOwner(5L)).contains(USER);
        assertThat(adminOrders.findOwner(9999L)).isEmpty();

        assertThat(adminOrders.executeIfNew(5L)).isTrue();
        assertThat(adminOrders.executeIfNew(5L)).isFalse();

        OrderRecord executed = orderLedger.findRecordById(5L).orElseThrow();
        assertThat(executed.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(executed.price()).isEqualTo(Money.of("710.00"));
        assertThat(executed.ticker()).isEqualTo("LOMA");

        CashBalance balance = ledger.cashBalance(USER);
        assertThat(balance.cash()).isEqualTo(Money.of("717500.00"));
        assertThat(balance.reserved()).isEqualTo(Money.of("90000.00"));
        assertThat(balance.available()).isEqualTo(Money.of("627500.00"));

        assertThat(adminOrders.executeIfNew(2L)).isFalse();
        assertThat(adminOrders.executeIfNew(3L)).isFalse();
    }

    @Test
    @Tag("CU-I.04")
    @Tag("CU-I.05")
    @Transactional
    void deletesOrderRowAndFreesIdempotencyKey() {
        assertThat(adminOrders.delete(1L)).isTrue();
        assertThat(adminOrders.delete(1L)).isFalse();
        assertThat(orderLedger.findRecordById(1L)).isEmpty();
        assertThat(ledger.cashBalance(USER).cash()).isEqualTo(Money.of("-247000.00"));

        String fingerprint = RequestFingerprint.of("k-del").hex();
        long insertedId = jdbc.sql("""
                INSERT INTO orders (instrumentid, userid, size, price, side, status, type, datetime,
                                    idempotency_key, request_fingerprint)
                VALUES (47, 1, 10, 925.85, 'BUY', 'FILLED', 'MARKET', CURRENT_TIMESTAMP, 'k-del', :fp)
                RETURNING id
                """).param("fp", fingerprint).query(Long.class).single();
        assertThat(orderRepository.findIdempotencyRecord(USER, "k-del")).isPresent();

        assertThat(adminOrders.delete(insertedId)).isTrue();
        assertThat(orderRepository.findIdempotencyRecord(USER, "k-del")).isEmpty();
    }
}
