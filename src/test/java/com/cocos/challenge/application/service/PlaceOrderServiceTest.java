package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.PlaceOrderCommand;
import com.cocos.challenge.application.port.out.AccountLock;
import com.cocos.challenge.application.port.out.BusinessMetrics;
import com.cocos.challenge.application.port.out.IdempotencyRecord;
import com.cocos.challenge.application.port.out.InstrumentRepository;
import com.cocos.challenge.application.port.out.LedgerReader;
import com.cocos.challenge.application.port.out.MarketDataRepository;
import com.cocos.challenge.application.port.out.NewOrder;
import com.cocos.challenge.application.port.out.OrderRepository;
import com.cocos.challenge.domain.exception.InvalidRequestException;
import com.cocos.challenge.domain.exception.NotFoundException;
import com.cocos.challenge.domain.model.Account;
import com.cocos.challenge.domain.model.CashBalance;
import com.cocos.challenge.domain.model.Instrument;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.InstrumentType;
import com.cocos.challenge.domain.model.MarketQuote;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.domain.model.Order;
import com.cocos.challenge.domain.model.OrderRequest;
import com.cocos.challenge.domain.model.OrderSide;
import com.cocos.challenge.domain.model.OrderStatus;
import com.cocos.challenge.domain.model.OrderType;
import com.cocos.challenge.domain.model.RejectionReason;
import com.cocos.challenge.domain.model.ShareBalance;
import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.domain.service.OrderEvaluator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class PlaceOrderServiceTest {

    private static final UserId USER = new UserId(1);
    private static final Instant NOW = Instant.parse("2026-11-21T14:36:00.123456789Z");
    private final FakePorts ports = new FakePorts();
    private final PlaceOrderService service = new PlaceOrderService(
            ports, ports, ports, ports, ports, new OrderEvaluator(), ports, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void newOrderUsesTheLedgerAndRecordsMetricsInOrder() {
        var result = service.place(buy(USER, "k", 10));

        assertThat(result.replayed()).isFalse();
        assertThat(result.order()).isEqualTo(new Order(1, USER, new InstrumentId(47), "PAMP",
                OrderSide.BUY, OrderType.MARKET, OrderStatus.FILLED, 10, Money.of("925.85"),
                null, null, Instant.parse("2026-11-21T14:36:00.123456Z"), "k",
                buy(USER, "k", 10).request().fingerprint()));
        assertThat(ports.calls).containsSubsequence("lock", "wait", "instrument", "quote", "cash",
                "shares", "save", "BUY MARKET FILLED null");
        assertThat(ports.orders).containsExactly(result.order());
    }

    @Test
    @Tag("CU-03.20")
    void unknownUserStopsAtTheLock() {
        assertThatThrownBy(() -> service.place(buy(new UserId(999), "k", 1)))
                .isInstanceOf(NotFoundException.class).hasMessage("User 999 was not found.");
        assertThat(ports.calls).containsExactly("lock");
    }

    @Test
    @Tag("CU-03.21")
    void unknownInstrumentStoresNothing() {
        assertThatThrownBy(() -> service.place(new PlaceOrderCommand(USER, "k",
                OrderRequest.of(999L, "BUY", "MARKET", 1, null, null))))
                .isInstanceOf(InvalidRequestException.class).hasMessage(
                        "Request validation failed: 'instrumentId' does not reference an existing instrument.");
        assertThat(ports.calls).containsSubsequence("lock", "wait", "instrument")
                .doesNotContain("quote", "cash", "shares", "save");
        assertThat(ports.orders).isEmpty();
    }

    @Test
    @Tag("CU-03.28")
    void currencyCannotBeTraded() {
        assertThatThrownBy(() -> service.place(new PlaceOrderCommand(USER, "k",
                OrderRequest.of(66L, "BUY", "MARKET", 1, null, null))))
                .isInstanceOf(InvalidRequestException.class).hasMessage(
                        "Request validation failed: 'instrumentId' must reference a tradable instrument; ARS cannot be bought or sold.");
        assertThat(ports.calls).containsSubsequence("lock", "wait", "instrument")
                .doesNotContain("quote", "cash", "shares", "save");
        assertThat(ports.orders).isEmpty();
    }

    @Test
    void rejectedOrderIsStoredAndRecordsItsReason() {
        var result = service.place(buy(USER, "k", 1000));

        assertThat(result.replayed()).isFalse();
        assertThat(result.order().status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(result.order().rejection().reason()).isEqualTo(RejectionReason.INSUFFICIENT_FUNDS);
        assertThat(ports.orders).containsExactly(result.order());
        assertThat(ports.calls).containsSubsequence("save", "BUY MARKET REJECTED INSUFFICIENT_FUNDS");
    }

    private static PlaceOrderCommand buy(UserId user, String key, int quantity) {
        return new PlaceOrderCommand(user, key, OrderRequest.of(47L, "BUY", "MARKET", quantity, null, null));
    }

    private static class FakePorts implements AccountLock, OrderRepository, InstrumentRepository,
            LedgerReader, MarketDataRepository, BusinessMetrics {
        final List<String> calls = new ArrayList<>();
        final List<Order> orders = new ArrayList<>();

        public Optional<Account> lock(UserId user) {
            calls.add("lock");
            return user.value() == 999 ? Optional.empty() : Optional.of(new Account(user, "1000" + user.value()));
        }

        public Optional<IdempotencyRecord> findIdempotencyRecord(UserId user, String key) {
            calls.add("key");
            return Optional.empty();
        }

        public Order findById(long id) {
            calls.add("find");
            throw new AssertionError("No existing order was found");
        }

        public Order save(NewOrder order) {
            calls.add("save");
            long id = orders.size() + 1L;
            Order saved = new Order(id, order.userId(), order.instrumentId(), order.ticker(), order.side(), order.type(),
                    order.status(), order.quantity(), order.price(), order.requestedAmount(), order.rejection(),
                    order.createdAt(), order.idempotencyKey(), order.fingerprint());
            orders.add(saved);
            return saved;
        }

        public Optional<Instrument> findById(InstrumentId id) {
            calls.add("instrument");
            return switch ((int) id.value()) {
                case 47 -> Optional.of(new Instrument(id, "PAMP", InstrumentType.ACCIONES));
                case 66 -> Optional.of(new Instrument(id, "ARS", InstrumentType.MONEDA));
                default -> Optional.empty();
            };
        }

        public Optional<MarketQuote> latestQuote(InstrumentId id) {
            calls.add("quote");
            return Optional.of(new MarketQuote(Money.of("925.85"), null));
        }

        public CashBalance cashBalance(UserId user) {
            calls.add("cash");
            return new CashBalance(Money.of("753000.00"), Money.of("125500.00"));
        }

        public ShareBalance shareBalance(UserId user, InstrumentId id) {
            calls.add("shares");
            return new ShareBalance(40, 0);
        }

        public void accountLockWaited(Duration duration) {
            assertThat(duration.isNegative()).isFalse();
            calls.add("wait");
        }

        public void orderProcessed(OrderSide side, OrderType type, OrderStatus status, RejectionReason reason) {
            calls.add(side + " " + type + " " + status + " " + reason);
        }

        public void idempotentReplay(OrderSide side) {
            calls.add("replay " + side);
        }

        public void idempotencyConflict() {
            calls.add("conflict");
        }

        public void orderCancelled(boolean alreadyCancelled) {
            throw new AssertionError("Order placement cannot cancel orders");
        }
    }
}
