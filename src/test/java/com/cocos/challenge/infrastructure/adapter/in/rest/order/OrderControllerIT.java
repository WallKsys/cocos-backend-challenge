package com.cocos.challenge.infrastructure.adapter.in.rest.order;

import com.cocos.challenge.support.IntegrationTest;
import com.cocos.challenge.support.SeedData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class OrderControllerIT {

    private static final String MARKET_BUY =
            """
            {"instrumentId":47,"side":"BUY","type":"MARKET","quantity":1}
            """;

    @Autowired
    private RestTestClient client;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void resetDatabase() {
        SeedData.reset(jdbc);
    }

    @Test
    @Tag("CU-03.23")
    void rejectsFractionalQuantityWithoutStoringAnOrder() {
        rejects(MARKET_BUY.replace("\"quantity\":1", "\"quantity\":1.5"), "fraction",
                "VALIDATION_ERROR", "Request validation failed: 'quantity' has an invalid type or format.");
    }

    @Test
    @Tag("CU-03.29")
    void requiresIdempotencyKeyWithoutStoringAnOrder() {
        rejects(MARKET_BUY, null, "MISSING_IDEMPOTENCY_KEY",
                "The 'Idempotency-Key' header is required for this operation.");
    }

    @Test
    @Tag("CU-03.30")
    void rejectsUserIdInTheBodyWithoutStoringAnOrder() {
        rejects(MARKET_BUY.replace("{", "{\"userId\":2,"), "unknown-field", "VALIDATION_ERROR",
                "Request validation failed: 'userId' is not a recognized field.");
    }

    @Test
    void returnsEntireOrderAndReplaysTheSameBody() {
        long before = orderCount();
        byte[] original = post(MARKET_BUY, "replay").expectStatus().isCreated()
                .expectHeader().doesNotExist("Idempotent-Replayed")
                .expectBody()
                .jsonPath("$.orderId").isNumber()
                .jsonPath("$.instrumentId").isEqualTo(47)
                .jsonPath("$.ticker").isEqualTo("PAMP")
                .jsonPath("$.side").isEqualTo("BUY")
                .jsonPath("$.type").isEqualTo("MARKET")
                .jsonPath("$.status").isEqualTo("FILLED")
                .jsonPath("$.quantity").isEqualTo(1)
                .jsonPath("$.price").isEqualTo("925.85")
                .jsonPath("$.totalAmount").isEqualTo("925.85")
                .jsonPath("$.createdAt").value(String.class, value -> assertThat(value).endsWith("Z"))
                .jsonPath("$.requestedAmount").hasJsonPath()
                .jsonPath("$.requestedAmount").isEmpty()
                .jsonPath("$.remainingAmount").hasJsonPath()
                .jsonPath("$.remainingAmount").isEmpty()
                .jsonPath("$.rejection").hasJsonPath()
                .jsonPath("$.rejection").isEmpty()
                .returnResult().getResponseBody();

        byte[] replay = post(MARKET_BUY, "replay").expectStatus().isOk()
                .expectHeader().valueEquals("Idempotent-Replayed", "true")
                .expectBody().returnResult().getResponseBody();
        assertThat(replay).isEqualTo(original);
        assertThat(orderCount()).isEqualTo(before + 1);
    }

    @Test
    void returnsTheFullTotalOfAnUnaffordableLimitOrder() {
        long pgr = jdbc.sql("SELECT id FROM instruments WHERE ticker = 'PGR'").query(Long.class).single();
        post("""
                {"instrumentId":%d,"side":"BUY","type":"LIMIT","quantity":2,"price":"9000000000000000.00"}
                """.formatted(pgr), "large-total").expectStatus().isCreated().expectBody()
                .jsonPath("$.status").isEqualTo("REJECTED")
                .jsonPath("$.rejection.code").isEqualTo("INSUFFICIENT_FUNDS")
                .jsonPath("$.quantity").isEqualTo(2)
                .jsonPath("$.price").isEqualTo("9000000000000000.00")
                .jsonPath("$.totalAmount").isEqualTo("18000000000000000.00");
    }

    private void rejects(String body, String key, String code, String detail) {
        long before = orderCount();
        post(body, key).expectStatus().isBadRequest().expectBody()
                .jsonPath("$.code").isEqualTo(code)
                .jsonPath("$.detail").isEqualTo(detail);
        assertThat(orderCount()).isEqualTo(before);
    }

    private RestTestClient.ResponseSpec post(String body, String key) {
        var request = client.post().uri("/api/v1/orders").header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return request.body(body).exchange();
    }

    private long orderCount() {
        return jdbc.sql("SELECT COUNT(*) FROM orders").query(Long.class).single();
    }
}
