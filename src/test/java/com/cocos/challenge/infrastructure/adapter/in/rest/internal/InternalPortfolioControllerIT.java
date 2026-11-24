package com.cocos.challenge.infrastructure.adapter.in.rest.internal;

import com.cocos.challenge.support.IntegrationTest;
import com.cocos.challenge.support.SeedData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

@IntegrationTest
@Tag("integration")
@TestPropertySource(properties = "cocos.internal-tools.enabled=true")
class InternalPortfolioControllerIT {

    @Autowired
    RestTestClient client;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void resetSeed() {
        SeedData.reset(jdbc);
    }

    @Test
    @Tag("CU-01.10")
    void onlyAdministratorCanSeeAnotherUsersPortfolio() {
        client.get().uri("/api/internal/users/1/portfolio").header("X-Role", "ADMIN").exchange()
                .expectStatus().isOk().expectBody()
                .jsonPath("$.totalValue").isEqualTo("889756.00");

        client.get().uri("/api/internal/users/1/portfolio").exchange()
                .expectStatus().isNotFound().expectBody()
                .jsonPath("$.code").isEqualTo("NOT_FOUND");
    }

    @Test
    void zeroUserIdViolatesDec26() {
        client.get().uri("/api/internal/users/0/portfolio").header("X-Role", "ADMIN").exchange()
                .expectStatus().isBadRequest().expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.detail").isEqualTo("Request validation failed: 'userId' must be greater than 0.");
    }
}
