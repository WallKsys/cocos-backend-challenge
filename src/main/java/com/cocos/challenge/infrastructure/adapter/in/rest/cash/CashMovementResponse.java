package com.cocos.challenge.infrastructure.adapter.in.rest.cash;

import com.cocos.challenge.infrastructure.adapter.in.rest.order.OrderResponse.RejectionResponse;

import java.time.Instant;

public record CashMovementResponse(
        long movementId, String type, String status, String amount, Instant createdAt, RejectionResponse rejection) {
}
