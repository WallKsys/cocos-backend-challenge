package com.cocos.challenge.infrastructure.adapter.in.rest.order;

import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Tag("integration")
class ConcurrentOrdersIT extends OrderLoadSupport {

    @Test
    @Tag("CU-03.36")
    void simultaneousBuysCannotSpendTheSameCash() throws Exception {
        long before = orderCount();
        assertThat(availableCash()).isEqualByComparingTo("627500.00");

        var responses = simultaneously(2, index -> {
            var response = buy("competing-" + index, 400);
            assertThat(availableCash()).isNotNegative();
            return response;
        });

        assertThat(responses).allSatisfy(response -> assertThat(response.getStatus().value()).isEqualTo(201));
        var orders = responses.stream().map(EntityExchangeResult::getResponseBody).toList();
        assertThat(orders).extracting(order -> order.required("status").asText())
                .containsExactlyInAnyOrder("FILLED", "REJECTED");
        assertThat(orders).filteredOn(order -> order.required("status").asText().equals("REJECTED"))
                .singleElement().satisfies(order ->
                        assertThat(order.required("rejection").required("code").asText())
                                .isEqualTo("INSUFFICIENT_FUNDS"));
        assertThat(orderCount()).isEqualTo(before + 2);
        assertThat(availableCash()).isEqualByComparingTo("257160.00");
    }
}
