package com.cocos.challenge.infrastructure.adapter.in.rest.tracing;

import com.cocos.challenge.application.port.in.CancellationResult;
import com.cocos.challenge.application.port.in.CashMovementResult;
import com.cocos.challenge.application.port.in.PlaceOrderResult;
import com.cocos.challenge.domain.model.UserId;

/**
 * Structured operation logs for client mutations (TEC-27, spec 010 FR-006).
 */
public final class OperationLog {

    private OperationLog() {
    }

    public static void orderPlaced(UserId userId, String idempotencyKey, PlaceOrderResult result) {
    }

    public static void orderCancelled(UserId userId, CancellationResult result) {
    }

    public static void cashDeposited(UserId userId, String idempotencyKey, CashMovementResult result) {
    }

    public static void cashWithdrawn(UserId userId, String idempotencyKey, CashMovementResult result) {
    }
}
