package com.cocos.challenge.infrastructure.adapter.out.metrics;

import com.cocos.challenge.application.port.out.BusinessMetrics;
import com.cocos.challenge.domain.model.OrderSide;
import com.cocos.challenge.domain.model.OrderStatus;
import com.cocos.challenge.domain.model.OrderType;
import com.cocos.challenge.domain.model.RejectionReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;

public class MicrometerBusinessMetrics implements BusinessMetrics {

    private final MeterRegistry registry;

    public MicrometerBusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void orderProcessed(OrderSide side, OrderType type, OrderStatus status,
            RejectionReason rejectionReasonOrNull) {
        String rejectionReason = rejectionReasonOrNull == null ? "NONE" : rejectionReasonOrNull.name();
        counter("cocos.orders.processed", "side", side.name(), "type", type.name(), "status", status.name(),
                "rejection_reason", rejectionReason).increment();
    }

    @Override
    public void orderCancelled(boolean alreadyCancelled) {
        counter("cocos.orders.cancelled", "already_cancelled", Boolean.toString(alreadyCancelled)).increment();
    }

    @Override
    public void idempotentReplay(OrderSide side) {
        counter("cocos.idempotency.replays", "side", side.name()).increment();
    }

    @Override
    public void idempotencyConflict() {
        counter("cocos.idempotency.conflicts").increment();
    }

    @Override
    public void accountLockWaited(Duration waitTime) {
        Timer.builder("cocos.account.lock.wait").publishPercentileHistogram().register(registry).record(waitTime);
    }

    private Counter counter(String name, String... tags) {
        return registry.counter(name, tags);
    }
}
