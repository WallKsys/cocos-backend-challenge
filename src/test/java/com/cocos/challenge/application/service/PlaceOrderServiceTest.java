package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.PlaceOrderCommand;
import com.cocos.challenge.application.port.out.AccountRepository;
import com.cocos.challenge.application.port.out.BusinessMetrics;
import com.cocos.challenge.application.port.out.InstrumentRepository;
import com.cocos.challenge.application.port.out.LedgerReader;
import com.cocos.challenge.application.port.out.MarketDataRepository;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.application.port.out.OrderLedgerRepository.IdempotencyRecord;
import com.cocos.challenge.application.port.out.OrderLedgerRepository.NewOrder;
import com.cocos.challenge.domain.exception.IdempotencyConflictException;
import com.cocos.challenge.domain.exception.InvalidRequestException;
import com.cocos.challenge.domain.exception.NotFoundException;
import com.cocos.challenge.domain.model.Account;
import com.cocos.challenge.domain.model.CashBalance;
import com.cocos.challenge.domain.model.CashMovement;
import com.cocos.challenge.domain.model.CreatedUser;
import com.cocos.challenge.domain.model.Fill;
import com.cocos.challenge.domain.model.Instrument;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.InstrumentSearchCriteria;
import com.cocos.challenge.domain.model.InstrumentType;
import com.cocos.challenge.domain.model.MarketQuote;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.domain.model.Order;
import com.cocos.challenge.domain.model.OrderHistoryCriteria;
import com.cocos.challenge.domain.model.OrderRecord;
import com.cocos.challenge.domain.model.OrderRequest;
import com.cocos.challenge.domain.model.OrderSide;
import com.cocos.challenge.domain.model.OrderStatus;
import com.cocos.challenge.domain.model.OrderType;
import com.cocos.challenge.domain.model.RejectionReason;
import com.cocos.challenge.domain.model.RequestFingerprint;
import com.cocos.challenge.domain.model.ShareBalance;
import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.domain.service.OrderEvaluator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

    @Test
    void limitOrderUsesQuoteToRejectPriceOutsideTheBand() {
        var request = OrderRequest.of(47L, "BUY", "LIMIT", 10, null, "1111.03");

        var result = service.place(new PlaceOrderCommand(USER, "outside-band", request));

        assertThat(ports.orders).containsExactly(result.order());
        assertThat(result.order().status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(result.order().rejection().reason()).isEqualTo(RejectionReason.PRICE_OUT_OF_RANGE);
    }

    @Test
    void passesTheOriginalRequestedAmountToTheRepository() {
        var request = OrderRequest.of(47L, "BUY", "MARKET", null, "50000.00", null);

        service.place(new PlaceOrderCommand(USER, "buy-by-amount", request));

        assertThat(ports.lastSaved.requestedAmount()).isEqualTo(Money.of("50000.00"));
    }

    @Test
    @Tag("CU-03.31")
    void replaysOriginalOrderBeforeCheckingInstrumentAndLedger() {
        var command = buy(USER, "k1", 10);
        Order original = service.place(command).order();
        assertThat(ports.calls).startsWith("lock", "wait", "key", "instrument");
        ports.calls.clear();

        var replay = service.place(command);

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.order()).isEqualTo(original);
        assertThat(ports.orders).containsExactly(original);
        assertThat(ports.calls).containsExactly("lock", "wait", "key", "replay BUY", "find");
    }

    @Test
    @Tag("CU-03.32")
    void sameKeyWithChangedContentConflicts() {
        Order original = service.place(buy(USER, "k1", 10)).order();
        ports.calls.clear();

        assertConflict(buy(USER, "k1", 20));
        assertThat(ports.orders).containsExactly(original);
    }

    @Test
    @Tag("CU-03.33")
    void differentUserCanUseTheSameKey() {
        Order original = service.place(buy(USER, "k1", 10)).order();
        UserId otherUser = new UserId(2);

        var other = service.place(buy(otherUser, "k1", 10));

        assertThat(other.replayed()).isFalse();
        assertThat(other.order().userId()).isEqualTo(otherUser);
        assertThat(other.order().id()).isNotEqualTo(original.id());
        assertThat(other.order().rejection().reason()).isEqualTo(RejectionReason.INSUFFICIENT_FUNDS);
    }

    @Test
    @Tag("CU-03.34")
    void rejectedOrderReplaysAfterCashIncreases() {
        var command = buy(USER, "k2", 1000);
        Order original = service.place(command).order();
        assertThat(original.status()).isEqualTo(OrderStatus.REJECTED);
        ports.cash = Money.of("1753000.00");
        ports.calls.clear();

        var replay = service.place(command);

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.order()).isEqualTo(original);
        assertThat(ports.orders).containsExactly(original);
        assertThat(ports.calls).containsExactly("lock", "wait", "key", "replay BUY", "find");
    }

    @Test
    @Tag("CU-03.35")
    void invalidRequestLeavesTheKeyAvailable() {
        assertThatThrownBy(() -> service.place(buy(USER, "k3", 0)))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(ports.calls).isEmpty();
        assertThat(ports.keys).isEmpty();

        var valid = service.place(buy(USER, "k3", 1));

        assertThat(valid.replayed()).isFalse();
        assertThat(valid.order().status()).isEqualTo(OrderStatus.FILLED);
        assertThat(ports.calls).startsWith("lock", "wait", "key", "instrument");
        assertThat(ports.orders).containsExactly(valid.order());
    }

    @Test
    @Tag("CU-03.41")
    void identicalContentWithDifferentKeysCreatesTwoOrders() {
        var first = service.place(buy(USER, "k10", 10));
        var second = service.place(buy(USER, "k11", 10));

        assertThat(second.replayed()).isFalse();
        assertThat(second.order().id()).isNotEqualTo(first.order().id());
        assertThat(ports.orders).containsExactly(first.order(), second.order());
    }

    @Test
    @Tag("CU-03.42")
    void depositKeyConflictsWithoutLoadingTheDepositAsAnOrder() {
        ports.keys.put(new Key(USER, "d1"), new IdempotencyRecord(99, OrderSide.CASH_IN,
                RequestFingerprint.of("CASH_IN|amount=1234.56")));

        assertConflict(buy(USER, "d1", 1));
        assertThat(ports.orders).isEmpty();
    }

    private void assertConflict(PlaceOrderCommand command) {
        assertThatThrownBy(() -> service.place(command)).isInstanceOf(IdempotencyConflictException.class)
                .hasMessage("Idempotency key '" + command.idempotencyKey() + "' was already used for a different request.");
        assertThat(ports.calls).containsExactly("lock", "wait", "key", "conflict");
    }

    private static PlaceOrderCommand buy(UserId user, String key, int quantity) {
        return new PlaceOrderCommand(user, key, OrderRequest.of(47L, "BUY", "MARKET", quantity, null, null));
    }

    private record Key(UserId user, String key) {}

    private static class FakePorts implements AccountRepository, OrderLedgerRepository, InstrumentRepository,
            LedgerReader, MarketDataRepository, BusinessMetrics {
        final List<String> calls = new ArrayList<>();
        final List<Order> orders = new ArrayList<>();
        final Map<Key, IdempotencyRecord> keys = new HashMap<>();
        Money cash = Money.of("753000.00");
        NewOrder lastSaved;

        public Optional<Account> lock(UserId user) {
            calls.add("lock");
            return user.value() == 999 ? Optional.empty() : Optional.of(new Account(user, "1000" + user.value()));
        }

        public Optional<Account> find(UserId user) {
            throw new UnsupportedOperationException();
        }

        public CreatedUser create(String email) {
            throw new UnsupportedOperationException();
        }

        public Optional<IdempotencyRecord> findIdempotencyRecord(UserId user, String key) {
            calls.add("key");
            return Optional.ofNullable(keys.get(new Key(user, key)));
        }

        public Order findById(long id) {
            calls.add("find");
            return orders.stream().filter(order -> order.id() == id).findFirst().orElseThrow();
        }

        public Order save(NewOrder order) {
            calls.add("save");
            lastSaved = order;
            long id = orders.size() + 1L;
            Order saved = new Order(id, order.userId(), order.instrumentId(), order.ticker(), order.side(), order.type(),
                    order.status(), order.quantity(), order.price(), order.requestedAmount(), order.rejection(),
                    order.createdAt(), order.idempotencyKey(), order.fingerprint());
            orders.add(saved);
            keys.put(new Key(order.userId(), order.idempotencyKey()),
                    new IdempotencyRecord(id, order.side(), order.fingerprint()));
            return saved;
        }

        public CashMovement findCashMovement(long id) {
            throw new UnsupportedOperationException();
        }

        public CashMovement save(NewCashMovement movement) {
            throw new UnsupportedOperationException();
        }

        public Optional<OrderRecord> findRecord(UserId userId, long orderId) {
            throw new UnsupportedOperationException();
        }

        public Optional<OrderRecord> findRecordById(long orderId) {
            throw new UnsupportedOperationException();
        }

        public boolean cancelIfNew(UserId userId, long orderId) {
            throw new UnsupportedOperationException();
        }

        public List<OrderRecord> history(UserId userId, OrderHistoryCriteria criteria, int maxResults) {
            throw new UnsupportedOperationException();
        }

        public Optional<Instrument> findById(InstrumentId id) {
            calls.add("instrument");
            return switch ((int) id.value()) {
                case 47 -> Optional.of(new Instrument(id, "PAMP", InstrumentType.ACCIONES));
                case 66 -> Optional.of(new Instrument(id, "ARS", InstrumentType.MONEDA));
                default -> Optional.empty();
            };
        }

        public Optional<InstrumentId> findCashInstrument() {
            throw new UnsupportedOperationException();
        }

        public List<RankedInstrument> search(InstrumentSearchCriteria criteria, int maxResults) {
            throw new UnsupportedOperationException();
        }

        public Optional<MarketQuote> latestQuote(InstrumentId id) {
            calls.add("quote");
            return Optional.of(new MarketQuote(Money.of("925.85"), null));
        }

        public StoredMarketData upsert(InstrumentId id, Money close, Money previousClose, LocalDate date) {
            throw new UnsupportedOperationException();
        }

        public CashBalance cashBalance(UserId user) {
            calls.add("cash");
            return USER.equals(user) ? new CashBalance(cash, Money.of("125500.00"))
                    : new CashBalance(Money.ZERO, Money.ZERO);
        }

        public ShareBalance shareBalance(UserId user, InstrumentId id) {
            calls.add("shares");
            return new ShareBalance(40, 0);
        }

        public List<OpenPosition> openPositions(UserId user) {
            throw new UnsupportedOperationException();
        }

        public Map<InstrumentId, List<Fill>> fills(UserId user, Set<InstrumentId> instrumentIds) {
            throw new UnsupportedOperationException();
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
