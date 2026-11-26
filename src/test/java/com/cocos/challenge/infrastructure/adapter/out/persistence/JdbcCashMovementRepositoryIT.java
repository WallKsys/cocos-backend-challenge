package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.NewCashMovement;
import com.cocos.challenge.domain.model.CashMovement;
import com.cocos.challenge.domain.model.CashMovementRequest;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.domain.model.OrderSide;
import com.cocos.challenge.domain.model.OrderStatus;
import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Tag("integration")
@Transactional
class JdbcCashMovementRepositoryIT {

    @Autowired
    JdbcClient jdbc;

    @Test
    @Tag("CU-05.01")
    void storesDepositAsFilledMarketCashInOnArs() {
        var repository = new JdbcCashMovementRepository(jdbc);
        CashMovement saved = deposit(repository, "100000.00");

        var row = jdbc.sql("""
                SELECT o.side, o.status, o.type AS order_type, o.size, o.price, o.requested_amount,
                       i.ticker, i.type AS instrument_type
                FROM orders o JOIN instruments i ON i.id = o.instrumentid
                WHERE o.id = :id
                """).param("id", saved.id()).query().singleRow();
        assertThat(row).containsEntry("side", "CASH_IN").containsEntry("status", "FILLED")
                .containsEntry("order_type", "MARKET").containsEntry("ticker", "ARS")
                .containsEntry("instrument_type", "MONEDA").containsEntry("requested_amount", null)
                .containsEntry("size", new BigDecimal("100000.00")).containsEntry("price", new BigDecimal("1.00"));
    }

    @Test
    @Tag("CU-05.02")
    void roundTripsDepositWithCents() {
        var repository = new JdbcCashMovementRepository(jdbc);
        CashMovement saved = deposit(repository, "1234.56");
        CashMovementRequest request = CashMovementRequest.of(OrderSide.CASH_IN, "1234.56");

        assertThat(repository.findById(saved.id())).isEqualTo(new CashMovement(saved.id(), new UserId(2),
                OrderSide.CASH_IN, OrderStatus.FILLED, Money.of("1234.56"), null,
                Instant.parse("2026-11-21T14:36:00.123456Z"), "deposit", request.fingerprint()));
    }

    private CashMovement deposit(JdbcCashMovementRepository repository, String amount) {
        CashMovementRequest request = CashMovementRequest.of(OrderSide.CASH_IN, amount);
        return repository.save(new NewCashMovement(new UserId(2),
                new JdbcInstrumentRepository(jdbc).findCashInstrument().orElseThrow(),
                request.side(), OrderStatus.FILLED, request.amount(), null,
                Instant.parse("2026-11-21T14:36:00.123456Z"), "deposit", request.fingerprint()));
    }
}
