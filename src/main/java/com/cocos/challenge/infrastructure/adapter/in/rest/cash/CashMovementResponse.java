package com.cocos.challenge.infrastructure.adapter.in.rest.cash;

import com.cocos.challenge.domain.model.CashMovement;
import com.cocos.challenge.infrastructure.adapter.in.rest.format.ApiDecimals;
import com.cocos.challenge.infrastructure.adapter.in.rest.order.OrderResponse.RejectionResponse;

import java.time.Instant;

public record CashMovementResponse(
        long movementId, String type, String status, String amount, Instant createdAt, RejectionResponse rejection) {

    static CashMovementResponse from(CashMovement movement) {
        var rejection = movement.rejection();
        return new CashMovementResponse(movement.id(), movement.side().name(), movement.status().name(),
                ApiDecimals.format(movement.amount()), movement.createdAt(),
                rejection == null ? null : new RejectionResponse(rejection.reason().name(), rejection.message()));
    }
}
