package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.CashMovementCommand;
import com.cocos.challenge.application.port.out.AccountRepository;
import com.cocos.challenge.application.port.out.BusinessMetrics;
import com.cocos.challenge.application.port.out.InstrumentRepository;
import com.cocos.challenge.application.port.out.LedgerReader;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.application.port.out.OrderLedgerRepository.IdempotencyRecord;
import com.cocos.challenge.application.port.out.OrderLedgerRepository.NewCashMovement;
import com.cocos.challenge.domain.exception.IdempotencyConflictException;
import com.cocos.challenge.domain.exception.NotFoundException;
import com.cocos.challenge.domain.model.Account;
import com.cocos.challenge.domain.model.CashBalance;
import com.cocos.challenge.domain.model.CashMovement;
import com.cocos.challenge.domain.model.CashMovementRequest;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.domain.model.OrderSide;
import com.cocos.challenge.domain.model.OrderStatus;
import com.cocos.challenge.domain.model.OrderType;
import com.cocos.challenge.domain.model.Rejection;
import com.cocos.challenge.domain.model.RejectionReason;
import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.domain.service.CashMovementEvaluator;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
class CashMovementServiceTest {

    private static final UserId USER = new UserId(1);
    private static final String KEY = "cash-key";
    private static final InstrumentId ARS = new InstrumentId(81);
    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00.123456789Z");
    private static final Instant STORED_AT = Instant.parse("2026-11-24T12:00:00.123456Z");
    private static final CashMovementRequest DEPOSIT = CashMovementRequest.of(OrderSide.CASH_IN, "100.00");
    private static final CashMovementRequest WITHDRAWAL = CashMovementRequest.of(OrderSide.CASH_OUT, "100.00");

    private final AccountRepository lock = mock(AccountRepository.class);
    private final OrderLedgerRepository orders = mock(OrderLedgerRepository.class);
    private final OrderLedgerRepository movements = mock(OrderLedgerRepository.class);
    private final InstrumentRepository instruments = mock(InstrumentRepository.class);
    private final LedgerReader ledger = mock(LedgerReader.class);
    private final BusinessMetrics metrics = mock(BusinessMetrics.class);
    private final CashMovementService service = new CashMovementService(lock, movements, instruments, ledger,
            new CashMovementEvaluator(), metrics, Clock.fixed(NOW, ZoneOffset.UTC),
            new IdempotencyLookup(orders, metrics));

    @BeforeEach
    void accountExists() {
        when(lock.lock(USER)).thenReturn(Optional.of(new Account(USER, "10001")));
    }

    @Test
    @Tag("CU-05.05")
    void depositReplayReturnsOriginalMovement() {
        CashMovement original = givenStoredMovement(DEPOSIT);

        var result = service.deposit(command(DEPOSIT));

        assertThat(result.movement()).isEqualTo(original);
        assertThat(result.replayed()).isTrue();
        verifyReplay(OrderSide.CASH_IN);
    }

    @Test
    @Tag("CU-06.04")
    void withdrawalReplayReturnsOriginalMovement() {
        CashMovement original = givenStoredMovement(WITHDRAWAL);

        var result = service.withdraw(command(WITHDRAWAL));

        assertThat(result.movement()).isEqualTo(original);
        assertThat(result.replayed()).isTrue();
        verifyReplay(OrderSide.CASH_OUT);
    }

    @Test
    @Tag("CU-05.07")
    void depositKeyConflictsWithDifferentAmountOrWithdrawal() {
        givenStoredMovement(DEPOSIT);

        assertConflict(() -> service.deposit(command(CashMovementRequest.of(OrderSide.CASH_IN, "200.00"))));
        assertConflict(() -> service.withdraw(command(WITHDRAWAL)));

        verify(metrics, times(2)).idempotencyConflict();
        verifyNoInteractions(ledger, movements);
    }

    @Test
    @Tag("CU-06.06")
    void withdrawalKeyConflictsWithDifferentAmount() {
        givenStoredMovement(WITHDRAWAL);

        assertConflict(() -> service.withdraw(command(CashMovementRequest.of(OrderSide.CASH_OUT, "50.00"))));

        verify(metrics).idempotencyConflict();
        verifyNoInteractions(ledger, movements);
    }

    @Test
    void depositPersistsCentsWithArsAndRecordsProcessedMetric() {
        var request = CashMovementRequest.of(OrderSide.CASH_IN, "123.45");
        CashMovement saved = givenNewMovement(request, OrderStatus.FILLED, null);

        var result = service.deposit(command(request));

        assertThat(result.movement()).isEqualTo(saved);
        assertThat(result.replayed()).isFalse();
        var calls = inOrder(lock, metrics, orders, ledger, instruments, movements);
        calls.verify(lock).lock(USER);
        calls.verify(metrics).accountLockWaited(any(Duration.class));
        calls.verify(orders).findIdempotencyRecord(USER, KEY);
        calls.verify(ledger).cashBalance(USER);
        calls.verify(instruments).findCashInstrument();
        calls.verify(movements).save(newMovement(request, OrderStatus.FILLED, null));
        calls.verify(metrics).orderProcessed(OrderSide.CASH_IN, OrderType.MARKET, OrderStatus.FILLED, null);
    }

    @Test
    void rejectedWithdrawalIsStoredAndCounted() {
        var rejection = new Rejection(RejectionReason.INSUFFICIENT_FUNDS,
                "Insufficient available cash in account 10001: required ARS 100.00, available ARS 50.00.");
        CashMovement saved = givenNewMovement(WITHDRAWAL, OrderStatus.REJECTED, rejection);

        var result = service.withdraw(command(WITHDRAWAL));

        assertThat(result.movement()).isEqualTo(saved);
        assertThat(result.replayed()).isFalse();
        verify(movements).save(newMovement(WITHDRAWAL, OrderStatus.REJECTED, rejection));
        verify(metrics).orderProcessed(OrderSide.CASH_OUT, OrderType.MARKET, OrderStatus.REJECTED,
                RejectionReason.INSUFFICIENT_FUNDS);
    }

    @Test
    void missingUserStopsBeforeIdempotencyLookup() {
        when(lock.lock(USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deposit(command(DEPOSIT)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User 1 was not found.");
        verifyNoInteractions(orders, ledger, movements);
    }

    private CashMovement givenStoredMovement(CashMovementRequest request) {
        CashMovement original = movement(request, OrderStatus.FILLED, null);
        when(orders.findIdempotencyRecord(USER, KEY)).thenReturn(Optional.of(
                new IdempotencyRecord(original.id(), original.side(), original.fingerprint())));
        when(movements.findCashMovement(original.id())).thenReturn(original);
        return original;
    }

    private void verifyReplay(OrderSide side) {
        var calls = inOrder(lock, metrics, orders, movements);
        calls.verify(lock).lock(USER);
        calls.verify(metrics).accountLockWaited(any(Duration.class));
        calls.verify(orders).findIdempotencyRecord(USER, KEY);
        calls.verify(metrics).idempotentReplay(side);
        calls.verify(movements).findCashMovement(42L);
        calls.verifyNoMoreInteractions();
        verifyNoInteractions(ledger);
    }

    private CashMovement givenNewMovement(CashMovementRequest request, OrderStatus status, Rejection rejection) {
        CashMovement saved = movement(request, status, rejection);
        when(ledger.cashBalance(USER)).thenReturn(new CashBalance(Money.of("150"), Money.of("100")));
        when(instruments.findCashInstrument()).thenReturn(Optional.of(ARS));
        when(movements.save(newMovement(request, status, rejection))).thenReturn(saved);
        return saved;
    }

    private static NewCashMovement newMovement(CashMovementRequest request, OrderStatus status, Rejection rejection) {
        return new NewCashMovement(USER, ARS, request.side(), status, request.amount(), rejection, STORED_AT,
                KEY, request.fingerprint());
    }

    private static CashMovement movement(CashMovementRequest request, OrderStatus status, Rejection rejection) {
        return new CashMovement(42L, USER, request.side(), status, request.amount(), rejection, STORED_AT,
                KEY, request.fingerprint());
    }

    private static CashMovementCommand command(CashMovementRequest request) {
        return new CashMovementCommand(USER, KEY, request);
    }

    private static void assertConflict(ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOf(IdempotencyConflictException.class)
                .hasMessage("Idempotency key 'cash-key' was already used for a different request.");
    }
}
