package com.cocos.challenge.infrastructure.adapter.in.rest.order;

import com.cocos.challenge.support.IntegrationTest;
import com.cocos.challenge.support.SeedData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

@IntegrationTest
@Tag("integration")
class OrderQueryControllerIT {

    @Autowired
    RestTestClient client;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void resetSeed() {
        SeedData.reset(jdbc);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "GET | /api/v1/orders/abc | 'orderId' has an invalid type or format.",
            "POST | /api/v1/orders/abc/cancel | 'orderId' has an invalid type or format.",
            "GET | /api/v1/orders/0 | 'orderId' must be greater than 0.",
            "POST | /api/v1/orders/0/cancel | 'orderId' must be greater than 0.",
            "GET | /api/v1/orders?status=UNKNOWN | 'status' must be one of NEW, FILLED, REJECTED, CANCELLED.",
            "GET | /api/v1/orders?cursor=invalid | 'cursor' is invalid."
    })
    void rejectsInvalidParameters(String method, String uri, String detail) {
        client.method(HttpMethod.valueOf(method)).uri(uri).header("X-User-Id", "1").exchange()
                .expectStatus().isBadRequest().expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.detail").isEqualTo("Request validation failed: " + detail);
    }

    @ParameterizedTest
    @CsvSource({
            "1, 1, 409, ORDER_NOT_CANCELLABLE, Order 1 cannot be cancelled because its status is FILLED. Only NEW orders can be cancelled.",
            "1, 8, 409, ORDER_NOT_CANCELLABLE, Order 8 cannot be cancelled because its status is FILLED. Only NEW orders can be cancelled.",
            "999, 7, 404, NOT_FOUND, User 999 was not found."
    })
    void rejectsCancellation(int user, int order, int status, String code, String detail) {
        client.post().uri("/api/v1/orders/{orderId}/cancel", order).header("X-User-Id", Integer.toString(user))
                .exchange().expectStatus().isEqualTo(status).expectBody()
                .jsonPath("$.code").isEqualTo(code).jsonPath("$.detail").isEqualTo(detail);
    }
}
