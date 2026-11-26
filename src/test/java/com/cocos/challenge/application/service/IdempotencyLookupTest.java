package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.out.BusinessMetrics;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.application.port.out.OrderLedgerRepository.IdempotencyRecord;
import com.cocos.challenge.domain.exception.IdempotencyConflictException;
import com.cocos.challenge.domain.model.OrderSide;
import com.cocos.challenge.domain.model.RequestFingerprint;
import com.cocos.challenge.domain.model.UserId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
class IdempotencyLookupTest {

    private static final UserId USER = new UserId(1);
    private static final String KEY = "d1";
    private static final RequestFingerprint DEPOSIT = RequestFingerprint.of("CASH_IN|amount=100000.00");

    private final OrderLedgerRepository orders = mock(OrderLedgerRepository.class);
    private final BusinessMetrics metrics = mock(BusinessMetrics.class);
    private final IdempotencyLookup lookup = new IdempotencyLookup(orders, metrics);

    @Test
    void unusedKeyReturnsEmpty() {
        when(orders.findIdempotencyRecord(USER, KEY)).thenReturn(Optional.empty());

        assertThat(lookup.find(USER, KEY, DEPOSIT)).isEmpty();
        verifyNoInteractions(metrics);
    }

    @Test
    void matchingFingerprintReturnsStoredIdAndRecordsReplay() {
        givenStoredDeposit();

        assertThat(lookup.find(USER, KEY, DEPOSIT)).hasValue(42L);
        verify(metrics).idempotentReplay(OrderSide.CASH_IN);
        verifyNoMoreInteractions(metrics);
    }

    @Test
    void differentFingerprintRecordsConflictAndThrows() {
        givenStoredDeposit();
        var withdrawal = RequestFingerprint.of("CASH_OUT|amount=100000.00");

        assertThatThrownBy(() -> lookup.find(USER, KEY, withdrawal))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessage("Idempotency key 'd1' was already used for a different request.");
        verify(metrics).idempotencyConflict();
        verifyNoMoreInteractions(metrics);
    }

    private void givenStoredDeposit() {
        when(orders.findIdempotencyRecord(USER, KEY))
                .thenReturn(Optional.of(new IdempotencyRecord(42L, OrderSide.CASH_IN, DEPOSIT)));
    }
}
