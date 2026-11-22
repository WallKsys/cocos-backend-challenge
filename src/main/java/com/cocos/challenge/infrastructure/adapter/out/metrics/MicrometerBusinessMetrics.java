package com.cocos.challenge.infrastructure.adapter.out.metrics;

import com.cocos.challenge.application.port.out.BusinessMetrics;
import com.cocos.challenge.domain.model.OrderSide;
import com.cocos.challenge.domain.model.OrderStatus;
import com.cocos.challenge.domain.model.OrderType;
import com.cocos.challenge.domain.model.RejectionReason;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;

public class MicrometerBusinessMetrics implements BusinessMetrics {

    public MicrometerBusinessMetrics(MeterRegistry registry) {
    }

    @Override
    public void orderProcessed(OrderSide side, OrderType type, OrderStatus status,
            RejectionReason rejectionReasonOrNull) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void orderCancelled(boolean alreadyCancelled) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void idempotentReplay(OrderSide side) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void idempotencyConflict() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void accountLockWaited(Duration waitTime) {
        throw new UnsupportedOperationException();
    }
}
