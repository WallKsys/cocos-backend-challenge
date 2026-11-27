package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.out.AccountRepository;
import com.cocos.challenge.application.port.out.AdminOrderRepository;
import com.cocos.challenge.application.port.out.InstrumentRepository;
import com.cocos.challenge.application.port.out.MarketDataRepository;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.application.port.out.SeedDataRestorer;
import com.cocos.challenge.domain.exception.NotFoundException;
import com.cocos.challenge.domain.model.Account;
import com.cocos.challenge.domain.model.UserId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
class InternalToolsServiceDeleteOrderTest {

    private static final UserId USER = new UserId(1);

    private final AccountRepository lock = mock(AccountRepository.class);
    private final AdminOrderRepository adminOrders = mock(AdminOrderRepository.class);
    private final InternalToolsService service = new InternalToolsService(lock, adminOrders,
            mock(OrderLedgerRepository.class), mock(InstrumentRepository.class), mock(MarketDataRepository.class),
            mock(SeedDataRestorer.class), mock(Clock.class));

    @Test
    @Tag("CU-I.04")
    @Tag("CU-I.05")
    void locksOwnerBeforeDeletingOrder() {
        when(adminOrders.findOwner(1L)).thenReturn(Optional.of(USER));
        when(lock.lock(USER)).thenReturn(Optional.of(new Account(USER, "10001")));
        when(adminOrders.delete(1L)).thenReturn(true);

        service.deleteOrder(1L);

        var calls = inOrder(adminOrders, lock);
        calls.verify(adminOrders).findOwner(1L);
        calls.verify(lock).lock(USER);
        calls.verify(adminOrders).delete(1L);
    }

    @Test
    void throwsNotFoundWhenOrderDoesNotExist() {
        when(adminOrders.findOwner(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteOrder(999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Order 999 was not found.");
        verifyNoInteractions(lock);
        verify(adminOrders, never()).delete(999L);
    }

    @Test
    void throwsNotFoundWhenOrderDisappearsBeforeDelete() {
        when(adminOrders.findOwner(1L)).thenReturn(Optional.of(USER));
        when(lock.lock(USER)).thenReturn(Optional.of(new Account(USER, "10001")));
        when(adminOrders.delete(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteOrder(1L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Order 1 was not found.");
    }
}
