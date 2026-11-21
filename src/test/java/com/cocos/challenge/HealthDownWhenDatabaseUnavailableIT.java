package com.cocos.challenge;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Spec 001 US1 scenario 3: when the database is unreachable the health endpoint reports DOWN (HTTP 503).
 *
 * <p>This test needs its own container (not the shared one of {@code @IntegrationTest}) because it stops
 * it on purpose, and {@code @DirtiesContext} so the broken context is never reused.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@DirtiesContext
class HealthDownWhenDatabaseUnavailableIT {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18"));

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    RestTestClient restTestClient;

    @Test
    void healthIsDownWhenTheDatabaseIsUnavailable() {
        POSTGRES.stop();

        restTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.status").isEqualTo("DOWN");
    }
}
