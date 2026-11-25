package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.out.AccountLock;
import com.cocos.challenge.application.port.out.AdminOrderRepository;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.domain.exception.DomainException;
import com.cocos.challenge.domain.exception.NotFoundException;
import com.cocos.challenge.domain.exception.OrderNotExecutableException;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
class ExecuteOrderServiceTest {

    private static final UserId USER = new UserId(1);

    private final AccountLock lock = mock(AccountLock.class);
    private final AdminOrderRepository adminOrders = mock(AdminOrderRepository.class);
    private final OrderLedgerRepository ledger = mock(OrderLedgerRepository.class);
    private final ExecuteOrderService service = new ExecuteOrderService(lock, adminOrders, ledger);

    @Test
    @Tag("CU-I.02")
    @Tag("CU-I.09")
    void locksOwnerExecutesPendingOrderAndReturnsRecord() {
        OrderRecord filled = record(OrderStatus.FILLED);
        when(adminOrders.findOwner(5L)).thenReturn(Optional.of(USER));
        when(lock.lock(USER)).thenReturn(Optional.of(new Account(USER, "10001")));
        when(adminOrders.executeIfNew(5L)).thenReturn(true);
        when(ledger.findRecordById(5L)).thenReturn(Optional.of(filled));

        OrderRecord result = service.execute(5L);

        assertThat(result).isEqualTo(filled);
        var calls = inOrder(adminOrders, lock, ledger);
        calls.verify(adminOrders).findOwner(5L);
        calls.verify(lock).lock(USER);
        calls.verify(adminOrders).executeIfNew(5L);
        calls.verify(ledger).findRecordById(5L);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"FILLED", "CANCELLED", "REJECTED"})
    @Tag("CU-I.03")
    void rejectsNonNewOrderWithCurrentStatus(OrderStatus status) {
        when(adminOrders.findOwner(5L)).thenReturn(Optional.of(USER));
        when(lock.lock(USER)).thenReturn(Optional.of(new Account(USER, "10001")));
        when(adminOrders.executeIfNew(5L)).thenReturn(false);
        when(ledger.findRecordById(5L)).thenReturn(Optional.of(record(status)));

        assertThat(OrderNotExecutableException.class.getSuperclass()).isEqualTo(DomainException.class);
        assertThatThrownBy(() -> service.execute(5L))
                .isInstanceOf(OrderNotExecutableException.class)
                .hasMessage("Order 5 cannot be executed because its status is " + status
                        + ". Only NEW orders can be executed.");
    }

    @Test
    void throwsNotFoundWhenOrderDoesNotExist() {
        when(adminOrders.findOwner(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Order 999 was not found.");
        verifyNoInteractions(lock, ledger);
    }

    @Test
    void throwsNotFoundWhenOrderDisappearsAfterLock() {
        when(adminOrders.findOwner(5L)).thenReturn(Optional.of(USER));
        when(lock.lock(USER)).thenReturn(Optional.of(new Account(USER, "10001")));
        when(adminOrders.executeIfNew(5L)).thenReturn(false);
        when(ledger.findRecordById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(5L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Order 5 was not found.");
    }

    private static OrderRecord record(OrderStatus status) {
        return new OrderRecord(5L, new InstrumentId(55), "LOMA", OrderSide.BUY, OrderType.LIMIT, status,
                new BigDecimal("50.00"), Money.of("710.00"), null, null, Instant.EPOCH);
    }
}
