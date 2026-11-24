package com.cocos.challenge.domain.model;

public record CashMovementRequest(OrderSide side, Money amount) {

    public static CashMovementRequest of(OrderSide side, String amount) {
        return new CashMovementRequest(side, Money.of(amount));
    }

    public RequestFingerprint fingerprint() {
        throw new UnsupportedOperationException();
    }
}
