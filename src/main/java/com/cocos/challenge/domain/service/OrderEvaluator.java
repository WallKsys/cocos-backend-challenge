package com.cocos.challenge.domain.service;

import com.cocos.challenge.domain.model.Account;
import com.cocos.challenge.domain.model.CashBalance;
import com.cocos.challenge.domain.model.Instrument;
import com.cocos.challenge.domain.model.MarketQuote;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.domain.model.OrderRequest;
import com.cocos.challenge.domain.model.OrderSide;
import com.cocos.challenge.domain.model.OrderStatus;
import com.cocos.challenge.domain.model.OrderType;
import com.cocos.challenge.domain.model.Rejection;
import com.cocos.challenge.domain.model.RejectionReason;
import com.cocos.challenge.domain.model.ShareBalance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Pure business rules that decide the outcome of an order (spec 005, plan.md § Order evaluation).
 */
public class OrderEvaluator {

    private static final BigDecimal LOWER_PRICE_FACTOR = new BigDecimal("0.80");
    private static final BigDecimal UPPER_PRICE_FACTOR = new BigDecimal("1.20");
    private static final BigDecimal MAX_QUANTITY = BigDecimal.valueOf(Integer.MAX_VALUE);

    public OrderEvaluation evaluate(
            OrderRequest request, Instrument instrument, Optional<MarketQuote> quote, Account account,
            CashBalance cash, ShareBalance shares) {
        Money price = referencePrice(request, quote);
        if (price == null) {
            long quantity = request.byQuantity() ? request.quantity() : 0;
            return rejected(RejectionReason.NO_MARKET_PRICE,
                    "No market price is available for " + instrument.ticker()
                            + "; MARKET orders cannot be executed.",
                    quantity, null);
        }
        if (request.type() == OrderType.LIMIT && quote.isPresent()) {
            Rejection rejection = priceBandRejection(instrument, price, quote.get().close());
            if (rejection != null) {
                long quantity = request.byQuantity() ? request.quantity() : 0;
                return new OrderEvaluation(OrderStatus.REJECTED, quantity, price, rejection);
            }
        }

        BigDecimal calculatedQuantity = quantity(request, price);
        // DEC-30: check the maximum before converting to an integer or computing the total.
        if (calculatedQuantity.compareTo(MAX_QUANTITY) > 0) {
            return rejected(RejectionReason.QUANTITY_TOO_LARGE,
                    "The amount ARS " + request.amount().amount().toPlainString() + " would "
                            + (request.side() == OrderSide.BUY ? "buy" : "sell")
                            + " more than " + Integer.MAX_VALUE + " shares of " + instrument.ticker()
                            + " at ARS " + price.amount().toPlainString() + ".",
                    0, price);
        }
        long quantity = calculatedQuantity.longValueExact();
        if (quantity == 0) {
            return rejected(RejectionReason.AMOUNT_BELOW_PRICE,
                    "The amount ARS " + request.amount().amount().toPlainString() + " is not enough to "
                            + (request.side() == OrderSide.BUY ? "buy" : "sell")
                            + " one share of " + instrument.ticker()
                            + " at ARS " + price.amount().toPlainString() + ".",
                    quantity, price);
        }
        OrderStatus status = request.type() == OrderType.MARKET ? OrderStatus.FILLED : OrderStatus.NEW;
        if (request.side() == OrderSide.BUY) {
            Money total = price.times(BigDecimal.valueOf(quantity));
            if (total.isGreaterThan(cash.available())) {
                return rejected(RejectionReason.INSUFFICIENT_FUNDS,
                        "Insufficient available cash in account " + account.accountNumber()
                                + ": required ARS " + total.amount().toPlainString()
                                + ", available ARS " + cash.available().amount().toPlainString() + ".",
                        quantity, price);
            }
        } else if (quantity > shares.available()) {
            return rejected(RejectionReason.INSUFFICIENT_SHARES,
                    "Insufficient available shares of " + instrument.ticker()
                            + " in account " + account.accountNumber()
                            + ": required " + quantity + ", available " + shares.available() + ".",
                    quantity, price);
        }
        return new OrderEvaluation(status, quantity, price, null);
    }

    private static Money referencePrice(OrderRequest request, Optional<MarketQuote> quote) {
        if (request.type() == OrderType.MARKET) {
            return quote.map(MarketQuote::close).orElse(null);
        }
        return request.price();
    }

    private static Rejection priceBandRejection(Instrument instrument, Money price, Money close) {
        // DEC-29: round the bounds inwards and allow both endpoints.
        BigDecimal min = close.amount().multiply(LOWER_PRICE_FACTOR).setScale(2, RoundingMode.CEILING);
        BigDecimal max = close.amount().multiply(UPPER_PRICE_FACTOR).setScale(2, RoundingMode.FLOOR);
        if (price.amount().compareTo(min) >= 0 && price.amount().compareTo(max) <= 0) {
            return null;
        }
        return new Rejection(RejectionReason.PRICE_OUT_OF_RANGE,
                "The limit price ARS " + price.amount().toPlainString()
                        + " is outside the allowed range for " + instrument.ticker()
                        + ": ARS " + min.toPlainString() + " to ARS " + max.toPlainString()
                        + " (20% around the last price ARS " + close.amount().toPlainString() + ").");
    }

    private static BigDecimal quantity(OrderRequest request, Money price) {
        if (request.byQuantity()) {
            return BigDecimal.valueOf(request.quantity());
        }
        return request.amount().amount().divide(price.amount(), 0, RoundingMode.DOWN);
    }

    private static OrderEvaluation rejected(
            RejectionReason reason, String message, long quantity, Money price) {
        return new OrderEvaluation(OrderStatus.REJECTED, quantity, price, new Rejection(reason, message));
    }

    /**
     * Outcome of evaluating an order: status, executed quantity, price (null without market price) and,
     * when rejected, the reason and message.
     */
    public record OrderEvaluation(OrderStatus status, long quantity, Money price, Rejection rejection) {
    }
}
