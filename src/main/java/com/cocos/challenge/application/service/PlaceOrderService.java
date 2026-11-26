package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.PlaceOrderCommand;
import com.cocos.challenge.application.port.in.PlaceOrderResult;
import com.cocos.challenge.application.port.in.PlaceOrderUseCase;
import com.cocos.challenge.application.port.out.AccountRepository;
import com.cocos.challenge.application.port.out.BusinessMetrics;
import com.cocos.challenge.application.port.out.InstrumentRepository;
import com.cocos.challenge.application.port.out.LedgerReader;
import com.cocos.challenge.application.port.out.MarketDataRepository;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.application.port.out.OrderLedgerRepository.NewOrder;
import com.cocos.challenge.domain.exception.InvalidRequestException;
import com.cocos.challenge.domain.exception.NotFoundException;
import com.cocos.challenge.domain.model.Account;
import com.cocos.challenge.domain.model.CashBalance;
import com.cocos.challenge.domain.model.Instrument;
import com.cocos.challenge.domain.model.InstrumentType;
import com.cocos.challenge.domain.model.MarketQuote;
import com.cocos.challenge.domain.model.Order;
import com.cocos.challenge.domain.model.OrderRequest;
import com.cocos.challenge.domain.model.Rejection;
import com.cocos.challenge.domain.model.RequestFingerprint;
import com.cocos.challenge.domain.model.ShareBalance;
import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.domain.service.OrderEvaluator;
import com.cocos.challenge.domain.service.OrderEvaluator.OrderEvaluation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.OptionalLong;

public class PlaceOrderService implements PlaceOrderUseCase {

    private final AccountRepository accountLock;
    private final OrderLedgerRepository orders;
    private final IdempotencyLookup idempotencyLookup;
    private final InstrumentRepository instruments;
    private final MarketDataRepository marketData;
    private final LedgerReader ledger;
    private final OrderEvaluator evaluator;
    private final BusinessMetrics metrics;
    private final Clock clock;

    public PlaceOrderService(AccountRepository accountLock, OrderLedgerRepository orders, InstrumentRepository instruments,
            MarketDataRepository marketData, LedgerReader ledger, OrderEvaluator evaluator,
            BusinessMetrics metrics, Clock clock) {
        this(accountLock, orders, instruments, marketData, ledger, evaluator, metrics, clock,
                new IdempotencyLookup(orders, metrics));
    }

    public PlaceOrderService(AccountRepository accountLock, OrderLedgerRepository orders, InstrumentRepository instruments,
            MarketDataRepository marketData, LedgerReader ledger, OrderEvaluator evaluator,
            BusinessMetrics metrics, Clock clock, IdempotencyLookup idempotencyLookup) {
        this.accountLock = accountLock;
        this.orders = orders;
        this.idempotencyLookup = idempotencyLookup;
        this.instruments = instruments;
        this.marketData = marketData;
        this.ledger = ledger;
        this.evaluator = evaluator;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PlaceOrderResult place(PlaceOrderCommand command) {
        long start = System.nanoTime();
        UserId user = command.userId();
        Account account = accountLock.lock(user)
                .orElseThrow(() -> new NotFoundException("User " + user.value() + " was not found."));
        metrics.accountLockWaited(Duration.ofNanos(System.nanoTime() - start));

        OrderRequest request = command.request();
        String key = command.idempotencyKey();
        RequestFingerprint fingerprint = request.fingerprint();
        OptionalLong existing = idempotencyLookup.find(user, key, fingerprint);
        if (existing.isPresent()) {
            return new PlaceOrderResult(orders.findById(existing.getAsLong()), true);
        }

        Instrument instrument = instruments.findById(request.instrumentId()).orElseThrow(() ->
                new InvalidRequestException(
                        "Request validation failed: 'instrumentId' does not reference an existing instrument."));
        if (instrument.type() == InstrumentType.MONEDA) {
            throw new InvalidRequestException(
                    "Request validation failed: 'instrumentId' must reference a tradable instrument; ARS cannot be bought or sold.");
        }

        Optional<MarketQuote> quote = marketData.latestQuote(instrument.id());
        CashBalance cash = ledger.cashBalance(user);
        ShareBalance shares = ledger.shareBalance(user, instrument.id());
        OrderEvaluation evaluation = evaluator.evaluate(request, instrument, quote, account, cash, shares);
        Rejection rejection = evaluation.rejection();
        Order saved = orders.save(new NewOrder(user, instrument.id(), instrument.ticker(), request.side(),
                request.type(), evaluation.status(), evaluation.quantity(), evaluation.price(), request.amount(),
                rejection, clock.instant().truncatedTo(ChronoUnit.MICROS), key, fingerprint));
        metrics.orderProcessed(request.side(), request.type(), evaluation.status(),
                rejection == null ? null : rejection.reason());
        return new PlaceOrderResult(saved, false);
    }
}
