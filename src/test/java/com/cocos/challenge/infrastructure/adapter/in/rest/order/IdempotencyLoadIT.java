package com.cocos.challenge.infrastructure.adapter.in.rest.order;

import com.cocos.challenge.domain.model.RequestFingerprint;
import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class IdempotencyLoadIT extends OrderLoadSupport {

    @Test
    void oneThousandSequentialRequestsMoveCashOnce() {
        long before = orderCount();
        BigDecimal initialCash = availableCash();

        var original = buy("sequential", 1);
        assertThat(original.getStatus().value()).isEqualTo(201);
        assertThat(original.getResponseHeaders().getFirst("Idempotent-Replayed")).isNull();
        assertThat(original.getResponseBody().required("status").asText()).isEqualTo("FILLED");
        long orderId = original.getResponseBody().required("orderId").longValue();

        for (int request = 1; request < 1000; request++) {
            assertReplay(buy("sequential", 1), orderId);
        }

        assertThat(orderCount()).isEqualTo(before + 1);
        assertThat(initialCash.subtract(availableCash())).isEqualByComparingTo("925.85");
    }

    @Test
    void fiftySimultaneousRequestsWithTheSameKeyExecuteOnce() throws Exception {
        long before = orderCount();
        BigDecimal initialCash = availableCash();

        var responses = simultaneously(50, index -> buy("simultaneous", 1));

        var originals = responses.stream().filter(response -> response.getStatus().value() == 201).toList();
        assertThat(originals).hasSize(1);
        var original = originals.getFirst();
        assertThat(original.getResponseHeaders().getFirst("Idempotent-Replayed")).isNull();
        assertThat(original.getResponseBody().required("status").asText()).isEqualTo("FILLED");
        long orderId = original.getResponseBody().required("orderId").longValue();
        responses.stream().filter(response -> response.getStatus().value() != 201)
                .forEach(response -> assertReplay(response, orderId));

        assertThat(orderCount()).isEqualTo(before + 1);
        assertThat(initialCash.subtract(availableCash())).isEqualByComparingTo("925.85");
    }

    @Test
    void conflictsWithChangedOrderContentAndWithADeposit() {
        assertThat(buy("existing-order", 1).getStatus().value()).isEqualTo(201);
        jdbc.sql("""
                INSERT INTO orders (instrumentid, userid, size, price, type, side, status, datetime,
                                    idempotency_key, request_fingerprint)
                VALUES (66, 1, 1234.56, 1.00, 'MARKET', 'CASH_IN', 'FILLED', CURRENT_TIMESTAMP,
                        'existing-deposit', :fingerprint)
                """).param("fingerprint", RequestFingerprint.of("CASH_IN|amount=1234.56").hex()).update();
        long before = orderCount();
        BigDecimal initialCash = availableCash();

        assertConflict(buy("existing-order", 2));
        assertConflict(buy("existing-deposit", 1));

        assertThat(orderCount()).isEqualTo(before);
        assertThat(availableCash()).isEqualByComparingTo(initialCash);
    }

    @Test
    void fiftyDistinctSimultaneousOrdersCannotOverspend() throws Exception {
        long before = orderCount();
        BigDecimal initialCash = availableCash();
        assertThat(new BigDecimal("18517.00").multiply(BigDecimal.valueOf(50))).isGreaterThan(initialCash);

        var responses = simultaneously(50, index -> {
            var response = buy("distinct-" + index, 20);
            assertThat(availableCash()).isNotNegative();
            return response;
        });

        assertThat(responses).allSatisfy(response -> assertThat(response.getStatus().value()).isEqualTo(201));
        var orders = responses.stream().map(EntityExchangeResult::getResponseBody).toList();
        assertThat(orders).extracting(order -> order.required("status").asText())
                .containsOnly("FILLED", "REJECTED");
        assertThat(orders).filteredOn(order -> order.required("status").asText().equals("REJECTED"))
                .allSatisfy(order -> assertThat(order.required("rejection").required("code").asText())
                        .isEqualTo("INSUFFICIENT_FUNDS"));

        BigDecimal filledTotal = orders.stream().filter(order -> order.required("status").asText().equals("FILLED"))
                .map(order -> new BigDecimal(order.required("totalAmount").asText()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(filledTotal).isPositive().isLessThanOrEqualTo(initialCash);
        assertThat(orderCount()).isEqualTo(before + 50);
        assertThat(availableCash()).isNotNegative().isEqualByComparingTo(initialCash.subtract(filledTotal));
    }

    private static void assertReplay(EntityExchangeResult<JsonNode> response, long orderId) {
        assertThat(response.getStatus().value()).isEqualTo(200);
        assertThat(response.getResponseHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(response.getResponseBody().required("orderId").longValue()).isEqualTo(orderId);
    }

    private static void assertConflict(EntityExchangeResult<JsonNode> response) {
        assertThat(response.getStatus().value()).isEqualTo(422);
        assertThat(response.getResponseBody().required("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
    }
}
