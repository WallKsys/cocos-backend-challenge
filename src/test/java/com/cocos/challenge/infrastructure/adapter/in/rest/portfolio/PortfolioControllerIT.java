package com.cocos.challenge.infrastructure.adapter.in.rest.portfolio;

import com.cocos.challenge.support.IntegrationTest;
import com.cocos.challenge.support.SeedData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

@IntegrationTest
@Tag("integration")
class PortfolioControllerIT {

    @Autowired
    RestTestClient client;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void resetSeed() {
        SeedData.reset(jdbc);
    }

    @Test
    @Tag("CU-01.09")
    void userCanOnlySeeTheirOwnPortfolio() {
        client.get().uri("/api/v1/portfolio").header("X-User-Id", "2").exchange()
                .expectStatus().isOk().expectBody()
                .jsonPath("$.positions").isEmpty()
                .jsonPath("$.totalValue").isEqualTo("0.00");

        client.get().uri("/api/v1/users/1/portfolio").header("X-User-Id", "2").exchange()
                .expectStatus().isNotFound().expectBody()
                .jsonPath("$.code").isEqualTo("NOT_FOUND");
    }
}
