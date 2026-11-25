package com.cocos.challenge.infrastructure.adapter.in.rest.order;

import com.cocos.challenge.application.port.in.CancelOrderUseCase;
import com.cocos.challenge.domain.model.OrderStatus;
import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Tag("integration")
class CancellationLoadIT extends OrderLoadSupport {

    @Autowired
    private CancelOrderUseCase cancellation;

    @Test
    void fiftySimultaneousCancellationsReleaseOneReservation() throws Exception {
        assertThat(availableCash()).isEqualByComparingTo("627500.00");

        var results = simultaneously(50, worker -> cancellation.cancel(new UserId(1), 7));

        assertThat(results).allSatisfy(result ->
                assertThat(result.order().status()).isEqualTo(OrderStatus.CANCELLED));
        assertThat(results.stream().filter(result -> !result.alreadyCancelled()).toList())
                .hasSize(1);
        assertThat(availableCash()).isEqualByComparingTo("717500.00");
    }
}
