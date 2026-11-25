package com.cocos.challenge.infrastructure.adapter.in.rest.internal;

import com.cocos.challenge.support.IntegrationTest;
import com.cocos.challenge.support.SeedData;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

@IntegrationTest
@Tag("integration")
@TestPropertySource(properties = "cocos.internal-tools.enabled=true")
class InternalOrderHistoryControllerIT {

    @Autowired
    RestTestClient client;

    @Autowired
    JdbcClient jdbc;

    @Test
    @Tag("CU-07.06")
    void administratorCanListAnotherUsersOrders() {
        SeedData.reset(jdbc);
        client.get().uri("/api/internal/users/1/orders").header("X-Role", "ADMIN").exchange()
                .expectStatus().isOk().expectBody()
                .jsonPath("$.items[*].orderId").isEqualTo(List.of(11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1));
        client.get().uri("/api/internal/users/1/orders").exchange()
                .expectStatus().isNotFound().expectBody().jsonPath("$.code").isEqualTo("NOT_FOUND");
    }

    @Test
    void rejectsZeroUserId() {
        client.get().uri("/api/internal/users/0/orders").header("X-Role", "ADMIN").exchange()
                .expectStatus().isBadRequest().expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.detail").isEqualTo("Request validation failed: 'userId' must be greater than 0.")
                .jsonPath("$.errors").isArray();
    }
}
