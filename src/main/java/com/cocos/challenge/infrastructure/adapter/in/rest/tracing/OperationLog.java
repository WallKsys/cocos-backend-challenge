package com.cocos.challenge.infrastructure.adapter.in.rest.tracing;

import com.cocos.challenge.application.port.in.CancellationResult;
import com.cocos.challenge.application.port.in.CashMovementResult;
import com.cocos.challenge.application.port.in.PlaceOrderResult;
import com.cocos.challenge.domain.model.Order;
import com.cocos.challenge.domain.model.OrderRecord;
import com.cocos.challenge.domain.model.OrderStatus;
import com.cocos.challenge.domain.model.Rejection;
import com.cocos.challenge.domain.model.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * Structured operation logs for client mutations (TEC-27, spec 010 FR-006).
 */
public final class OperationLog {

    private static final Logger LOGGER = LoggerFactory.getLogger(OperationLog.class);

    private OperationLog() {
    }

    public static void orderPlaced(UserId userId, String idempotencyKey, PlaceOrderResult result) {
        Order order = result.order();
        log("PLACE_ORDER", userId, order.id(), idempotencyKey, order.status(), order.rejection(), result.replayed());
    }

    public static void orderCancelled(UserId userId, CancellationResult result) {
        OrderRecord order = result.order();
        log("CANCEL_ORDER", userId, order.id(), null, order.status(), order.rejection(), result.alreadyCancelled());
    }

    public static void cashDeposited(UserId userId, String idempotencyKey, CashMovementResult result) {
        var movement = result.movement();
        log("DEPOSIT", userId, movement.id(), idempotencyKey, movement.status(), movement.rejection(),
                result.replayed());
    }

    public static void cashWithdrawn(UserId userId, String idempotencyKey, CashMovementResult result) {
        var movement = result.movement();
        log("WITHDRAW", userId, movement.id(), idempotencyKey, movement.status(), movement.rejection(),
                result.replayed());
    }

    private static void log(String operation, UserId userId, long orderId, String idempotencyKey,
            OrderStatus status, Rejection rejection, boolean replayed) {
        LoggingEventBuilder event = LOGGER.atInfo()
                .addKeyValue("operation", operation)
                .addKeyValue("userId", userId.value())
                .addKeyValue("orderId", orderId);
        if (idempotencyKey != null) {
            event = event.addKeyValue("idempotencyKey", idempotencyKey);
        }
        event = event.addKeyValue("result", status.name());
        if (status == OrderStatus.REJECTED && rejection != null) {
            event = event.addKeyValue("rejectionReason", rejection.reason().name());
        }
        event.addKeyValue("replayed", replayed)
                .log("Operation completed");
    }
}
