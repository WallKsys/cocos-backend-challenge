package com.cocos.challenge.domain.model;

import com.cocos.challenge.domain.exception.InvalidRequestException;

/**
 * Validated BUY/SELL order request (spec 005, plan.md § Validation and § Request fingerprint).
 */
public record OrderRequest(
        InstrumentId instrumentId, OrderSide side, OrderType type, Long quantity, Money amount, Money price) {

    public static OrderRequest of(
            Long instrumentId, String side, String type, Integer quantity, String amount, String price) {
        OrderSide parsedSide = parseSide(side);
        OrderType parsedType = parseType(type);
        if ((quantity == null) == (amount == null)) {
            throw new InvalidRequestException(
                    "Request validation failed: exactly one of 'quantity' or 'amount' is required.");
        }
        Long parsedQuantity = quantity == null ? null : quantity.longValue();
        if (parsedQuantity != null && parsedQuantity < 1) {
            throw new InvalidRequestException("Request validation failed: 'quantity' must be greater than 0.");
        }
        Money parsedAmount = amount == null ? null : Money.of(amount);
        if (parsedAmount != null && parsedAmount.compareTo(Money.ZERO) <= 0) {
            throw new InvalidRequestException("Request validation failed: 'amount' must be greater than 0.");
        }
        Money parsedPrice = price == null || price.isBlank() ? null : Money.of(price);
        if (parsedType == OrderType.LIMIT && parsedPrice == null) {
            throw new InvalidRequestException("Request validation failed: 'price' is required for LIMIT orders.");
        }
        if (parsedType == OrderType.LIMIT && parsedPrice.compareTo(Money.ZERO) <= 0) {
            throw new InvalidRequestException("Request validation failed: 'price' must be greater than 0.");
        }
        if (parsedType == OrderType.MARKET && parsedPrice != null) {
            throw new InvalidRequestException("Request validation failed: 'price' must not be sent for MARKET orders.");
        }
        return new OrderRequest(
                new InstrumentId(instrumentId), parsedSide, parsedType, parsedQuantity, parsedAmount, parsedPrice);
    }

    public boolean byQuantity() {
        return quantity != null;
    }

    public boolean byAmount() {
        return amount != null;
    }

    public RequestFingerprint fingerprint() {
        return RequestFingerprint.of("ORDER|instrumentId=" + instrumentId.value()
                + "|side=" + side.name()
                + "|type=" + type.name()
                + "|quantity=" + (quantity == null ? "" : quantity)
                + "|amount=" + (amount == null ? "" : amount.amount().toPlainString())
                + "|price=" + (price == null ? "" : price.amount().toPlainString()));
    }

    private static OrderSide parseSide(String side) {
        if (OrderSide.BUY.name().equals(side)) {
            return OrderSide.BUY;
        }
        if (OrderSide.SELL.name().equals(side)) {
            return OrderSide.SELL;
        }
        throw new InvalidRequestException("Request validation failed: 'side' must be BUY or SELL.");
    }

    private static OrderType parseType(String type) {
        if (OrderType.MARKET.name().equals(type)) {
            return OrderType.MARKET;
        }
        if (OrderType.LIMIT.name().equals(type)) {
            return OrderType.LIMIT;
        }
        throw new InvalidRequestException("Request validation failed: 'type' must be MARKET or LIMIT.");
    }
}
