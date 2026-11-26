package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.OrderLedgerRepository.IdempotencyRecord;
import com.cocos.challenge.application.port.out.OrderLedgerRepository.NewOrder;
import com.cocos.challenge.domain.exception.InvalidRequestException;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.domain.model.Order;
import com.cocos.challenge.domain.model.OrderRequest;
import com.cocos.challenge.domain.model.OrderSide;
import com.cocos.challenge.domain.model.OrderStatus;
import com.cocos.challenge.domain.model.OrderType;
import com.cocos.challenge.domain.model.Rejection;
import com.cocos.challenge.domain.model.RejectionReason;
import com.cocos.challenge.domain.model.RequestFingerprint;
import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@Tag("integration")
@Transactional
class JdbcOrderLedgerRepositoryPlacementIT {

    private static final UserId USER = new UserId(1);
    private static final InstrumentId PAMP = new InstrumentId(47);
    private static final Instant CREATED_AT = Instant.parse("2026-11-21T14:36:00.123456Z");
    private static final RequestFingerprint BUY_FINGERPRINT =
            OrderRequest.of(47L, "BUY", "MARKET", 1, null, null).fingerprint();

    @Autowired
    JdbcClient jdbc;

    @Test
    @Tag("CU-03.33")
    void oneKeyCanIdentifyDifferentOrdersForDifferentUsers() {
        JdbcOrderLedgerRepository repository = new JdbcOrderLedgerRepository(jdbc);
        UserId secondUser = new UserId(2);
        Order first = repository.save(buy(USER, "shared"));
        Order second = repository.save(buy(secondUser, "shared"));

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(repository.findIdempotencyRecord(USER, "shared"))
                .contains(new IdempotencyRecord(first.id(), OrderSide.BUY, BUY_FINGERPRINT));
        assertThat(repository.findIdempotencyRecord(secondUser, "shared"))
                .contains(new IdempotencyRecord(second.id(), OrderSide.BUY, BUY_FINGERPRINT));
    }

    @Test
    @Tag("CU-03.35")
    void aKeyWithNoStoredOrderCanBeUsedForAValidOrder() {
        JdbcOrderLedgerRepository repository = new JdbcOrderLedgerRepository(jdbc);
        String key = "retry-after-invalid-request";
        assertThatThrownBy(() -> OrderRequest.of(47L, "BUY", "MARKET", 0, null, null))
                .isInstanceOf(InvalidRequestException.class);

        assertThat(repository.findIdempotencyRecord(USER, key)).isEmpty();
        Order saved = repository.save(buy(USER, key));
        assertThat(repository.findIdempotencyRecord(USER, key))
                .contains(new IdempotencyRecord(saved.id(), OrderSide.BUY, BUY_FINGERPRINT));
    }

    @Test
    @Tag("CU-03.37")
    void savesAndReadsTheSameInstantInANonUtcSession() {
        jdbc.sql("SET LOCAL TIME ZONE 'America/Argentina/Buenos_Aires'").update();
        JdbcOrderLedgerRepository repository = new JdbcOrderLedgerRepository(jdbc);
        Order saved = repository.save(buy(USER, "timestamp"));

        assertThat(saved.createdAt()).isEqualTo(CREATED_AT);
        assertThat(repository.findById(saved.id()).createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    @Tag("CU-03.42")
    void findsAKeyUsedByAFractionalDepositWithoutMappingTheDepositToShares() {
        JdbcOrderLedgerRepository repository = new JdbcOrderLedgerRepository(jdbc);
        RequestFingerprint depositFingerprint = RequestFingerprint.of("CASH_IN|amount=1234.56");
        long depositId = jdbc.sql("""
                INSERT INTO orders (instrumentid, userid, size, price, type, side, status, datetime,
                                    idempotency_key, request_fingerprint)
                VALUES (66, 1, 1234.56, 1.00, 'MARKET', 'CASH_IN', 'FILLED', CURRENT_TIMESTAMP, 'deposit', :fingerprint)
                RETURNING id
                """).param("fingerprint", depositFingerprint.hex()).query(Long.class).single();

        assertThat(repository.findIdempotencyRecord(USER, "deposit"))
                .contains(new IdempotencyRecord(depositId, OrderSide.CASH_IN, depositFingerprint));
        assertThat(depositFingerprint).isNotEqualTo(BUY_FINGERPRINT);
    }

    @Test
    void persistsTheOriginalAmountAndRejectionForReplay() {
        JdbcOrderLedgerRepository repository = new JdbcOrderLedgerRepository(jdbc);
        Money amount = Money.of("800.00");
        Money price = Money.of("1000.00");
        Rejection rejection = new Rejection(RejectionReason.AMOUNT_BELOW_PRICE,
                "The amount ARS 800.00 is not enough to buy one share of PAMP at ARS 1000.00.");
        RequestFingerprint fingerprint = OrderRequest.of(47L, "BUY", "LIMIT", null, "800.00", "1000.00").fingerprint();
        NewOrder order = new NewOrder(USER, PAMP, "PAMP", OrderSide.BUY, OrderType.LIMIT, OrderStatus.REJECTED,
                0, price, amount, rejection, CREATED_AT, "below-price", fingerprint);

        Order saved = repository.save(order);
        Order replay = repository.findById(saved.id());
        assertThat(replay).isEqualTo(new Order(saved.id(), USER, PAMP, "PAMP", OrderSide.BUY, OrderType.LIMIT,
                OrderStatus.REJECTED, 0, price, amount, rejection, CREATED_AT, "below-price", fingerprint));
        assertThat(replay.totalAmount()).isEqualTo(Money.ZERO);
        assertThat(replay.remainingAmount()).isEqualTo(amount);
    }

    @Test
    void findByIdReadsTheTickerOfTheStoredInstrument() {
        JdbcOrderLedgerRepository repository = new JdbcOrderLedgerRepository(jdbc);
        RequestFingerprint fingerprint = OrderRequest.of(54L, "BUY", "MARKET", 1, null, null).fingerprint();
        NewOrder order = new NewOrder(USER, new InstrumentId(54), "METR", OrderSide.BUY, OrderType.MARKET,
                OrderStatus.FILLED, 1, Money.of("100.00"), null, null, CREATED_AT, "metr-ticker", fingerprint);

        Order saved = repository.save(order);

        assertThat(repository.findById(saved.id()).ticker()).isEqualTo("METR");
    }

    private static NewOrder buy(UserId user, String key) {
        return new NewOrder(user, PAMP, "PAMP", OrderSide.BUY, OrderType.MARKET, OrderStatus.FILLED,
                1, Money.of("925.85"), null, null, CREATED_AT, key, BUY_FINGERPRINT);
    }
}
