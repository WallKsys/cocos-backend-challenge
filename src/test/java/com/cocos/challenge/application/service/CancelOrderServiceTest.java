package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.out.AccountLock;
import com.cocos.challenge.application.port.out.BusinessMetrics;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.domain.exception.NotFoundException;
import com.cocos.challenge.domain.exception.OrderNotCancellableException;
import com.cocos.challenge.domain.model.Account;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.domain.model.OrderRecord;
import com.cocos.challenge.domain.model.OrderSide;
import com.cocos.challenge.domain.model.OrderStatus;
import com.cocos.challenge.domain.model.OrderType;
import com.cocos.challenge.domain.model.UserId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
class CancelOrderServiceTest {

    private static final UserId USER = new UserId(1);
    private static final long ORDER = 7;
    private final AccountLock lock = mock(AccountLock.class);
    private final OrderLedgerRepository ledger = mock(OrderLedgerRepository.class);
    private final BusinessMetrics metrics = mock(BusinessMetrics.class);

    @ParameterizedTest
    @EnumSource(value = OrderSide.class, names = {"BUY", "SELL"})
    @Tag("CU-04.01")
    @Tag("CU-04.02")
    void locksBeforeReadingAndCancelsPendingTrade(OrderSide side) {
        var service = serviceWithUser();
        when(ledger.findRecord(USER, ORDER)).thenReturn(Optional.of(record(side, OrderStatus.NEW)));
        when(ledger.cancelIfNew(USER, ORDER)).thenReturn(true);

        var result = service.cancel(USER, ORDER);

        assertThat(result.order()).isEqualTo(record(side, OrderStatus.CANCELLED));
        assertThat(result.alreadyCancelled()).isFalse();
        var calls = inOrder(lock, ledger, metrics);
        calls.verify(lock).lock(USER);
        calls.verify(ledger).findRecord(USER, ORDER);
        calls.verify(ledger).cancelIfNew(USER, ORDER);
        calls.verify(metrics).orderCancelled(false);
    }

    @Test
    @Tag("CU-04.03")
    void returnsAlreadyCancelledWithoutUpdating() {
        var service = serviceWithUser();
        var cancelled = record(OrderSide.BUY, OrderStatus.CANCELLED);
        when(ledger.findRecord(USER, ORDER)).thenReturn(Optional.of(cancelled));

        var result = service.cancel(USER, ORDER);

        assertThat(result.order()).isEqualTo(cancelled);
        assertThat(result.alreadyCancelled()).isTrue();
        verify(ledger, never()).cancelIfNew(USER, ORDER);
        verify(metrics).orderCancelled(true);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"FILLED", "REJECTED"})
    @Tag("CU-04.04")
    @Tag("CU-04.05")
    void refusesTerminalTrade(OrderStatus status) {
        var service = serviceWithUser();
        when(ledger.findRecord(USER, ORDER)).thenReturn(Optional.of(record(OrderSide.BUY, status)));

        assertThatThrownBy(() -> service.cancel(USER, ORDER))
                .isInstanceOf(OrderNotCancellableException.class)
                .hasMessage("Order 7 cannot be cancelled because its status is " + status
                        + ". Only NEW orders can be cancelled.");
        verify(ledger, never()).cancelIfNew(USER, ORDER);
        verifyNoInteractions(metrics);
    }

    @Test
    @Tag("CU-04.06")
    @Tag("CU-04.07")
    void hidesUnownedAndMissingRecords() {
        var service = serviceWithUser();

        assertThatThrownBy(() -> service.cancel(USER, ORDER))
                .isInstanceOf(NotFoundException.class).hasMessage("Order 7 was not found.");
        verify(ledger).findRecord(USER, ORDER);
        verifyNoMoreInteractions(ledger);
    }

    @Test
    void unknownUserStopsBeforeReadingOrders() {
        var service = new CancelOrderService(lock, ledger, metrics);

        assertThatThrownBy(() -> service.cancel(USER, ORDER))
                .isInstanceOf(NotFoundException.class).hasMessage("User 1 was not found.");
        verifyNoInteractions(ledger, metrics);
    }

    @Test
    void reReadsStatusWhenGuardedUpdateLoses() {
        var service = serviceWithUser();
        var pending = record(OrderSide.BUY, OrderStatus.NEW);
        var cancelled = record(OrderSide.BUY, OrderStatus.CANCELLED);
        when(ledger.findRecord(USER, ORDER)).thenReturn(Optional.of(pending), Optional.of(cancelled));

        assertThat(service.cancel(USER, ORDER).alreadyCancelled()).isTrue();
        verify(ledger, times(2)).findRecord(USER, ORDER);
        verify(ledger).cancelIfNew(USER, ORDER);
        verify(metrics).orderCancelled(true);
    }

    private CancelOrderService serviceWithUser() {
        when(lock.lock(USER)).thenReturn(Optional.of(new Account(USER, "account")));
        return new CancelOrderService(lock, ledger, metrics);
    }

    private static OrderRecord record(OrderSide side, OrderStatus status) {
        return new OrderRecord(ORDER, new InstrumentId(31), "BMA", side, OrderType.LIMIT, status,
                new BigDecimal("60.00"), Money.of("1500.00"), null, null, Instant.EPOCH);
    }
}
