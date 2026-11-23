package com.cocos.challenge.domain.model;

/**
 * Validation rules of plan.md § Validation (spec 005): checked in the documented order.
 */
public record OrderRequest(
        InstrumentId instrumentId, OrderSide side, OrderType type, Long quantity, Money amount, Money price) {

    public static OrderRequest of(
            Long instrumentId, String side, String type, Integer quantity, String amount, String price) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public boolean byQuantity() {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public boolean byAmount() {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public RequestFingerprint fingerprint() {
        throw new UnsupportedOperationException("Not implemented yet.");
    }
}
