package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.out.AccountRepository;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.domain.exception.NotFoundException;
import com.cocos.challenge.domain.model.Account;
import com.cocos.challenge.domain.model.OrderRecord;
import com.cocos.challenge.domain.model.UserId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
class GetOrderServiceTest {

    private final UserId user = new UserId(2);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final OrderLedgerRepository ledger = mock(OrderLedgerRepository.class);
    private final GetOrderService service = new GetOrderService(accounts, ledger);

    @Test
    void returnsTheRecordForTheRequestingUser() {
        when(accounts.find(user)).thenReturn(Optional.of(new Account(user, "10002")));
        OrderRecord order = mock(OrderRecord.class);
        when(ledger.findRecord(user, 7)).thenReturn(Optional.of(order));

        assertThat(service.get(user, 7)).isSameAs(order);
    }

    @Test
    @Tag("CU-07.05")
    void reportsAnOrderMissingFromTheRequestingUsersLedger() {
        when(accounts.find(user)).thenReturn(Optional.of(new Account(user, "10002")));
        when(ledger.findRecord(user, 7)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(user, 7))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Order 7 was not found.");
    }

    @Test
    void rejectsAnUnknownUserBeforeLookingUpTheOrder() {
        when(accounts.find(user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(user, 7))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User 2 was not found.");
        verifyNoInteractions(ledger);
    }
}
