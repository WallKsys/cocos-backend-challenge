package com.cocos.challenge.domain.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class OrderRecordTest {

    @Test
    @Tag("CU-07.03")
    void tradeKeepsItsRejectionAndComputesAmount() {
        Rejection rejection = new Rejection(RejectionReason.INSUFFICIENT_FUNDS, "Insufficient available cash.");
        OrderRecord trade = record(OrderSide.BUY, "10", Money.of("925.85"), rejection);

        assertThat(trade.rejection()).isEqualTo(rejection);
        assertThat(trade.quantity()).isEqualTo(10L);
        assertThat(trade.amount()).isEqualTo(Money.of("9258.50"));
    }

    @Test
    @Tag("CU-07.04")
    void rejectionMayBeAbsentAndUnpricedTradesHaveNoAmount() {
        assertThat(record(OrderSide.SELL, "100", Money.of("950"), null).rejection()).isNull();
        assertThat(record(OrderSide.BUY, "10", null, null).amount()).isNull();
    }

    @Test
    void cashMovementsUseSizeForAmountAndHaveNoShares() {
        for (OrderSide side : new OrderSide[]{OrderSide.CASH_IN, OrderSide.CASH_OUT}) {
            OrderRecord movement = record(side, "25.75", Money.of("1"), null);
            assertThat(movement.quantity()).isNull();
            assertThat(movement.amount()).isEqualTo(Money.of("25.75"));
        }
    }

    private static OrderRecord record(OrderSide side, String size, Money price, Rejection rejection) {
        OrderStatus status = side == OrderSide.BUY || side == OrderSide.SELL
                ? OrderStatus.REJECTED : OrderStatus.FILLED;
        return new OrderRecord(1, new InstrumentId(47), "PAMP", side, OrderType.MARKET,
                status, new BigDecimal(size), price, null, rejection, Instant.EPOCH);
    }
}
