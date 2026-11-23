package com.cocos.challenge.application.port.out;

import com.cocos.challenge.domain.model.OrderSide;
import com.cocos.challenge.domain.model.OrderStatus;
import com.cocos.challenge.domain.model.OrderType;
import com.cocos.challenge.domain.model.RejectionReason;

import java.time.Duration;

public interface BusinessMetrics {

    void orderProcessed(OrderSide side, OrderType type, OrderStatus status, RejectionReason rejectionReasonOrNull);

    void orderCancelled(boolean alreadyCancelled);

    void idempotentReplay(OrderSide side);

    void idempotencyConflict();

    void accountLockWaited(Duration waitTime);
}
