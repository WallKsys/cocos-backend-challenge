package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.domain.model.HistoryCursor;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.domain.model.OrderHistoryCriteria;
import com.cocos.challenge.domain.model.OrderRecord;
import com.cocos.challenge.domain.model.OrderSide;
import com.cocos.challenge.domain.model.OrderStatus;
import com.cocos.challenge.domain.model.OrderType;
import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Tag("integration")
class JdbcOrderLedgerRepositoryIT {

    private static final UserId OWNER = new UserId(1);
    private static final UserId OTHER_USER = new UserId(2);

    private final JdbcClient jdbc;
    private final JdbcOrderLedgerRepository repository;
    private final JdbcLedgerReader ledger;

    @Autowired
    JdbcOrderLedgerRepositoryIT(JdbcClient jdbc) {
        this.jdbc = jdbc;
        this.repository = new JdbcOrderLedgerRepository(jdbc);
        this.ledger = new JdbcLedgerReader(jdbc);
    }

    @Test
    @Tag("CU-04.01")
    @Tag("CU-04.03")
    @Transactional
    void cancelsPendingBuyAndReleasesCashOnce() {
        assertThat(ledger.cashBalance(OWNER).available()).isEqualTo(Money.of("627500.00"));

        assertThat(repository.cancelIfNew(OWNER, 7)).isTrue();
        assertThat(repository.findRecord(OWNER, 7).orElseThrow().status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(ledger.cashBalance(OWNER).available()).isEqualTo(Money.of("717500.00"));

        assertThat(repository.cancelIfNew(OWNER, 7)).isFalse();
        assertThat(ledger.cashBalance(OWNER).available()).isEqualTo(Money.of("717500.00"));
    }

    @Test
    @Tag("CU-04.02")
    @Transactional
    void cancelsPendingSellAndReleasesShares() {
        InstrumentId pamp = new InstrumentId(47);
        long orderId = jdbc.sql("""
                INSERT INTO orders (instrumentid, userid, size, price, side, status, type, datetime)
                VALUES (47, 1, 30, 950.00, 'SELL', 'NEW', 'LIMIT', CURRENT_TIMESTAMP)
                RETURNING id
                """).query(Long.class).single();

        assertThat(ledger.shareBalance(OWNER, pamp).available()).isEqualTo(10);
        assertThat(repository.cancelIfNew(OWNER, orderId)).isTrue();
        assertThat(ledger.shareBalance(OWNER, pamp).available()).isEqualTo(40);
    }

    @Test
    @Tag("CU-04.06")
    @Tag("CU-04.07")
    @Transactional
    void hidesOrdersBelongingToAnotherUserAndMissingOrders() {
        assertThat(repository.findRecord(OTHER_USER, 7)).isEmpty();
        assertThat(repository.findRecord(OWNER, 9999)).isEmpty();
        assertThat(repository.cancelIfNew(OTHER_USER, 7)).isFalse();
        assertThat(repository.cancelIfNew(OWNER, 9999)).isFalse();
        assertThat(repository.findRecord(OWNER, 7).orElseThrow().status()).isEqualTo(OrderStatus.NEW);
        assertThat(ledger.cashBalance(OWNER).available()).isEqualTo(Money.of("627500.00"));
    }

    @Test
    @Tag("CU-07.01")
    void listsOnlyTheUsersOrdersAndCashMovementsInUtc() {
        var criteria = new OrderHistoryCriteria(null, 20, null);
        var history = repository.history(OWNER, criteria, 21);

        assertThat(history).extracting(OrderRecord::id)
                .containsExactly(11L, 10L, 9L, 8L, 7L, 6L, 5L, 4L, 3L, 2L, 1L);
        assertThat(history.getFirst()).isEqualTo(new OrderRecord(11, new InstrumentId(31), "BMA",
                OrderSide.SELL, OrderType.MARKET, OrderStatus.FILLED,
                new BigDecimal("30.00"), Money.of("1530.00"), null, null,
                Instant.parse("2023-07-13T15:13:20Z")));
        assertThat(history.getLast().ticker()).isEqualTo("ARS");
        assertThat(history.getLast().side()).isEqualTo(OrderSide.CASH_IN);
        assertThat(history.getLast().createdAt()).isEqualTo(Instant.parse("2023-07-12T12:11:20Z"));
        assertThat(repository.history(OTHER_USER, criteria, 21)).isEmpty();
    }

    @Test
    @Tag("CU-07.02")
    @Tag("CU-07.08")
    void filtersHistoryByStatus() {
        assertPage(OrderStatus.NEW, null, 21, 7L, 5L);
        assertPage(OrderStatus.CANCELLED, null, 21, 3L);
        assertPage(OrderStatus.NEW, new HistoryCursor(Instant.parse("2023-07-13T11:13:20Z"), 7), 21, 5L);
    }

    @Test
    @Tag("CU-07.07")
    @Transactional
    void pagesThroughHistoryWithAnIdTieBreakerAtThePageBoundary() {
        jdbc.sql("UPDATE orders SET datetime = (SELECT datetime FROM orders WHERE id = 7) WHERE id = 6")
                .update();

        var first = assertPage(null, null, 5, 11L, 10L, 9L, 8L, 7L);
        var second = assertPage(null, cursorAfter(first), 5, 6L, 5L, 4L, 3L, 2L);
        assertPage(null, cursorAfter(second), 5, 1L);
    }

    private List<OrderRecord> assertPage(OrderStatus status, HistoryCursor cursor, int maxResults, Long... ids) {
        var page = repository.history(OWNER, new OrderHistoryCriteria(status, 5, cursor), maxResults);
        assertThat(page).extracting(OrderRecord::id).containsExactly(ids);
        return page;
    }

    private static HistoryCursor cursorAfter(List<OrderRecord> page) {
        OrderRecord last = page.getLast();
        return new HistoryCursor(last.createdAt(), last.id());
    }
}
