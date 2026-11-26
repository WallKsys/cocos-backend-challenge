package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.out.AccountRepository;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.domain.exception.NotFoundException;
import com.cocos.challenge.domain.model.Account;
import com.cocos.challenge.domain.model.CursorPage;
import com.cocos.challenge.domain.model.HistoryCursor;
import com.cocos.challenge.domain.model.OrderHistoryCriteria;
import com.cocos.challenge.domain.model.OrderRecord;
import com.cocos.challenge.domain.model.UserId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
class OrderQueryServiceListTest {

    private final UserId user = new UserId(1);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final OrderLedgerRepository ledger = mock(OrderLedgerRepository.class);
    private final OrderQueryService service = new OrderQueryService(accounts, ledger);

    @Test
    @Tag("CU-07.07")
    void buildsTheNextCursorFromTheLastReturnedOrder() {
        when(accounts.find(user)).thenReturn(Optional.of(new Account(user, "10001")));
        OrderHistoryCriteria criteria = OrderHistoryCriteria.of("NEW", 1, null);
        OrderRecord first = mock(OrderRecord.class);
        OrderRecord extra = mock(OrderRecord.class);
        Instant createdAt = Instant.parse("2023-07-13T11:13:20Z");
        when(first.id()).thenReturn(7L);
        when(first.createdAt()).thenReturn(createdAt);
        when(ledger.history(user, criteria, 2)).thenReturn(List.of(first, extra));

        CursorPage<OrderRecord, HistoryCursor> page = service.list(user, criteria);

        assertThat(page.items()).containsExactly(first);
        assertThat(page.nextCursor()).isEqualTo(new HistoryCursor(createdAt, 7));

        OrderHistoryCriteria next = OrderHistoryCriteria.of("NEW", 1, page.nextCursor().encode());
        when(ledger.history(user, next, 2)).thenReturn(List.of(extra));
        assertThat(service.list(user, next)).isEqualTo(new CursorPage<>(List.of(extra), null));
    }

    @Test
    @Tag("CU-07.09")
    void returnsAnEmptyPageWithoutACursorWhenTheFilterDoesNotMatch() {
        when(accounts.find(user)).thenReturn(Optional.of(new Account(user, "10001")));
        OrderHistoryCriteria criteria = OrderHistoryCriteria.of("CANCELLED", null, null);
        when(ledger.history(user, criteria, 21)).thenReturn(List.of());

        assertThat(service.list(user, criteria)).isEqualTo(new CursorPage<>(List.of(), null));
    }

    @Test
    void rejectsAnUnknownUserBeforeReadingHistory() {
        when(accounts.find(user)).thenReturn(Optional.empty());
        OrderHistoryCriteria criteria = OrderHistoryCriteria.of(null, null, null);

        assertThatThrownBy(() -> service.list(user, criteria))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User 1 was not found.");
        verifyNoInteractions(ledger);
    }
}
