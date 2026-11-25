package com.cocos.challenge.infrastructure.adapter.in.rest.order;

import java.time.Instant;

public record OrderRecordResponse(
        long orderId, long instrumentId, String ticker, String side, String type, String status,
        Long quantity, String price, String amount, String requestedAmount, Instant createdAt,
        OrderResponse.RejectionResponse rejection) {
}
