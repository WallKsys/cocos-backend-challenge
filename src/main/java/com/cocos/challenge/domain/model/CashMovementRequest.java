package com.cocos.challenge.domain.model;

import com.cocos.challenge.domain.exception.InvalidRequestException;

public record CashMovementRequest(OrderSide side, Money amount) {

    public CashMovementRequest {
        if (side != OrderSide.CASH_IN && side != OrderSide.CASH_OUT) {
            throw new IllegalArgumentException("Cash movement side must be CASH_IN or CASH_OUT.");
        }
        if (amount.compareTo(Money.ZERO) <= 0) {
            throw new InvalidRequestException("Request validation failed: 'amount' must be greater than 0.");
        }
    }

    public static CashMovementRequest of(OrderSide side, String amount) {
        return new CashMovementRequest(side, Money.of(amount));
    }

    public RequestFingerprint fingerprint() {
        return RequestFingerprint.of(side.name() + "|amount=" + amount.amount().toPlainString());
    }
}
