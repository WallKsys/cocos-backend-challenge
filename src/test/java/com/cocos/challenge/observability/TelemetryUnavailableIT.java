package com.cocos.challenge.observability;

import com.cocos.challenge.support.IntegrationTest;
import com.cocos.challenge.support.SeedData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Spec 010 FR-005: when the telemetry stack is unreachable, the application keeps working normally.
 */
@IntegrationTest
@TestPropertySource(properties = {
        "management.otlp.metrics.export.enabled=true",
        "management.otlp.metrics.export.url=http://127.0.0.1:1/v1/metrics",
        "management.tracing.export.otlp.enabled=true",
        "management.opentelemetry.tracing.export.otlp.endpoint=http://127.0.0.1:1/v1/traces",
        "management.logging.export.otlp.enabled=true",
        "management.opentelemetry.logging.export.otlp.endpoint=http://127.0.0.1:1/v1/logs"
})
@DirtiesContext
class TelemetryUnavailableIT {

    @Autowired
    private RestTestClient client;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void resetDatabase() {
        SeedData.reset(jdbc);
    }

    @Test
    void ordersAndPortfolioStillWorkWhenTelemetryEndpointIsUnavailable() {
        client.post().uri("/api/v1/orders")
                .header("X-User-Id", "1")
                .header("Idempotency-Key", "telemetry-unavailable-order")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"instrumentId":47,"side":"BUY","type":"MARKET","quantity":1}
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.status").isEqualTo("FILLED")
                .jsonPath("$.ticker").isEqualTo("PAMP");

        client.get().uri("/api/v1/portfolio")
                .header("X-User-Id", "1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalValue").isNotEmpty()
                .jsonPath("$.cash.available").isNotEmpty();
    }
}
