package com.cocos.challenge.infrastructure.adapter.in.rest.order;

import java.time.Instant;

public record OrderResponse(
        long orderId, long instrumentId, String ticker, String side, String type, String status,
        long quantity, String price, String totalAmount, String requestedAmount, String remainingAmount,
        Instant createdAt, RejectionResponse rejection) {

    public record RejectionResponse(String code, String message) {
    }
}
