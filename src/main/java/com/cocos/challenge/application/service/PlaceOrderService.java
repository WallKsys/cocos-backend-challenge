package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.PlaceOrderUseCase;
import com.cocos.challenge.application.port.out.BusinessMetrics;
import com.cocos.challenge.application.port.out.InstrumentRepository;
import com.cocos.challenge.application.port.out.LedgerReader;
import com.cocos.challenge.application.port.out.MarketDataRepository;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.application.port.out.OrderLedgerRepository.NewOrder;
import com.cocos.challenge.domain.exception.InvalidRequestException;
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

import java.util.Optional;
import java.util.OptionalLong;

public class PlaceOrderService implements PlaceOrderUseCase {

    private final IdempotentMutationGuard guard;
    private final OrderLedgerRepository orders;
    private final InstrumentRepository instruments;
    private final MarketDataRepository marketData;
    private final LedgerReader ledger;
    private final OrderEvaluator evaluator;
    private final BusinessMetrics metrics;

    public PlaceOrderService(IdempotentMutationGuard guard, OrderLedgerRepository orders,
            InstrumentRepository instruments, MarketDataRepository marketData, LedgerReader ledger,
            OrderEvaluator evaluator, BusinessMetrics metrics) {
        this.guard = guard;
        this.orders = orders;
        this.instruments = instruments;
        this.marketData = marketData;
        this.ledger = ledger;
        this.evaluator = evaluator;
        this.metrics = metrics;
    }

    @Override
    @Transactional
    public PlaceOrderResult place(PlaceOrderCommand command) {
        UserId user = command.userId();
        Account account = guard.lock(user);

        OrderRequest request = command.request();
        String key = command.idempotencyKey();
        RequestFingerprint fingerprint = request.fingerprint();
        OptionalLong existing = guard.find(user, key, fingerprint);
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
                rejection, guard.now(), key, fingerprint));
        metrics.orderProcessed(request.side(), request.type(), evaluation.status(),
                rejection == null ? null : rejection.reason());
        return new PlaceOrderResult(saved, false);
    }
}
