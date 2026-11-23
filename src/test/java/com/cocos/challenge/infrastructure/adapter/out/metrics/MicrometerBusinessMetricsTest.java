package com.cocos.challenge.infrastructure.adapter.out.metrics;

import com.cocos.challenge.domain.model.OrderSide;
import com.cocos.challenge.domain.model.OrderStatus;
import com.cocos.challenge.domain.model.OrderType;
import com.cocos.challenge.domain.model.RejectionReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class MicrometerBusinessMetricsTest {

    private SimpleMeterRegistry registry;
    private MicrometerBusinessMetrics metrics;
    private DistributionStatisticConfig accountLockWaitConfig;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        registry.config().meterFilter(new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                if (id.getName().equals("cocos.account.lock.wait")) {
                    accountLockWaitConfig = config;
                }
                return config;
            }
        });
        metrics = new MicrometerBusinessMetrics(registry);
    }

    @Test
    void orderProcessedRecordsAllTagsAndTheDefaultRejectionReason() {
        metrics.orderProcessed(OrderSide.BUY, OrderType.MARKET, OrderStatus.REJECTED,
                RejectionReason.INSUFFICIENT_FUNDS);
        metrics.orderProcessed(OrderSide.SELL, OrderType.LIMIT, OrderStatus.FILLED, null);
        assertThat(counter("cocos.orders.processed", "side", "BUY", "type", "MARKET", "status", "REJECTED",
                "rejection_reason", "INSUFFICIENT_FUNDS").count()).isEqualTo(1.0);
        assertThat(counter("cocos.orders.processed", "side", "SELL", "type", "LIMIT", "status", "FILLED",
                "rejection_reason", "NONE").count()).isEqualTo(1.0);
    }

    @Test
    void cancellationReplayAndConflictCountersUseOnlyTheirDocumentedTags() {
        metrics.orderCancelled(true);
        metrics.idempotentReplay(OrderSide.SELL);
        metrics.idempotencyConflict();
        assertThat(counter("cocos.orders.cancelled", "already_cancelled", "true").count()).isEqualTo(1.0);
        assertThat(counter("cocos.idempotency.replays", "side", "SELL").count()).isEqualTo(1.0);
        assertThat(counter("cocos.idempotency.conflicts").count()).isEqualTo(1.0);
    }

    @Test
    void accountLockWaitIsATimerWithAPercentileHistogram() {
        metrics.accountLockWaited(Duration.ofMillis(125));
        Timer timer = registry.get("cocos.account.lock.wait").timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(125.0);
        assertThat(timer.getId().getTags()).isEmpty();
        assertThat(accountLockWaitConfig.isPercentileHistogram()).isTrue();
    }

    @Test
    void noMeterHasBusinessIdentifierTags() {
        metrics.orderProcessed(OrderSide.BUY, OrderType.MARKET, OrderStatus.REJECTED,
                RejectionReason.INSUFFICIENT_FUNDS);
        metrics.orderCancelled(false);
        metrics.idempotentReplay(OrderSide.BUY);
        metrics.idempotencyConflict();
        metrics.accountLockWaited(Duration.ofMillis(1));
        Set<String> forbidden = Set.of("user", "user_id", "order", "order_id", "idempotency_key", "ticker");
        assertThat(registry.getMeters()).allSatisfy(meter -> assertThat(meter.getId().getTags())
                .noneMatch(tag -> forbidden.contains(tag.getKey())));
    }

    private Counter counter(String name, String... tags) {
        return registry.get(name).tags(tags).counter();
    }
}
