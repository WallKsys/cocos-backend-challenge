package com.cocos.challenge.volume;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 009 volume verification for PRD metrics M-08 to M-11 on 10,000,000 orders and 100,000 instruments.
 * Excluded from the default {@code ./mvnw verify} build and executed via {@code ./mvnw verify -Pvolume}.
 */
@Tag("volume")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@DirtiesContext
class VolumeIT {

    private static final int WARMUP_REQUESTS = 50;
    private static final int MEASURED_REQUESTS = 500;
    private static final String VOLUME_USER_ID = "500";

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))
            .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withShmSize(256L * 1024 * 1024));

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterAll
    static void stopContainer() {
        POSTGRES.stop();
    }

    @Autowired
    private RestTestClient client;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void meetsLatencyTargetsOnTenMillionOrders() throws Exception {
        long loadStartNanos = System.nanoTime();
        loadVolumeDataset();
        double loadSeconds = (System.nanoTime() - loadStartNanos) / 1_000_000_000.0;

        long ordersCount = countRows("orders");
        long instrumentsCount = countRows("instruments");
        long marketDataCount = countRows("marketdata");
        long usersCount = countRows("users");

        assertThat(ordersCount).isGreaterThanOrEqualTo(10_000_000L);
        assertThat(instrumentsCount).isGreaterThanOrEqualTo(100_000L);
        assertThat(marketDataCount).isGreaterThanOrEqualTo(200_000L);
        assertThat(usersCount).isGreaterThanOrEqualTo(1_000L);

        long v000001Id = jdbc.sql("SELECT id FROM instruments WHERE ticker = 'V000001'")
                .query(Long.class)
                .single();
        String secondPageCursor = fetchSecondPageCursor();

        AtomicInteger m10KeySeq = new AtomicInteger();
        String buyBody = "{\"instrumentId\":" + v000001Id + ",\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":1}";

        MetricResult m08 = measure("M-08", "GET /api/v1/portfolio", 200.0, 400.0, index -> {
            var spec = client.get().uri("/api/v1/portfolio")
                    .header("X-User-Id", VOLUME_USER_ID)
                    .exchange()
                    .expectStatus().isOk();
            if (index == -WARMUP_REQUESTS) {
                spec.expectBody().jsonPath("$.positions").isNotEmpty();
            } else {
                spec.expectBody().consumeWith(ignored -> {});
            }
        });

        MetricResult m09 = measure("M-09", "GET /api/v1/orders?limit=20", 100.0, 200.0, index -> {
            if (Math.floorMod(index, 2) == 0) {
                client.get().uri("/api/v1/orders?limit=20")
                        .header("X-User-Id", VOLUME_USER_ID)
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody().consumeWith(ignored -> {});
            } else {
                client.get().uri("/api/v1/orders?limit=20&cursor={cursor}", secondPageCursor)
                        .header("X-User-Id", VOLUME_USER_ID)
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody().consumeWith(ignored -> {});
            }
        });

        MetricResult m10 = measure("M-10", "POST /api/v1/orders (BUY MARKET)", 150.0, 300.0, index -> {
            String key = "volume-m10-" + m10KeySeq.incrementAndGet();
            var spec = client.post().uri("/api/v1/orders")
                    .header("X-User-Id", VOLUME_USER_ID)
                    .header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buyBody)
                    .exchange()
                    .expectStatus().isCreated();
            if (index == -WARMUP_REQUESTS) {
                spec.expectBody().jsonPath("$.status").isEqualTo("FILLED");
            } else {
                spec.expectBody().consumeWith(ignored -> {});
            }
        });

        MetricResult m11 = measure("M-11", "GET /api/v1/instruments?q=...", 100.0, 200.0, index -> {
            String term = Math.floorMod(index, 2) == 0 ? "12345" : "volume";
            var spec = client.get().uri("/api/v1/instruments?q={q}", term)
                    .exchange()
                    .expectStatus().isOk();
            if (index == -WARMUP_REQUESTS || index == -WARMUP_REQUESTS + 1) {
                spec.expectBody().jsonPath("$.items").isNotEmpty();
            } else {
                spec.expectBody().consumeWith(ignored -> {});
            }
        });

        List<MetricResult> results = List.of(m08, m09, m10, m11);
        printReport(loadSeconds, ordersCount, instrumentsCount, marketDataCount, usersCount, results);

        SoftAssertions.assertSoftly(softly -> {
            for (MetricResult result : results) {
                softly.assertThat(result.p95Ms())
                        .as("%s p95 latency (ms)", result.id())
                        .isLessThan(result.p95TargetMs());
                softly.assertThat(result.p99Ms())
                        .as("%s p99 latency (ms)", result.id())
                        .isLessThan(result.p99TargetMs());
            }
        });
    }

    private void loadVolumeDataset() throws Exception {
        String script = new ClassPathResource("volume/volume-dataset.sql").getContentAsString(StandardCharsets.UTF_8);
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                for (String rawChunk : script.split(";")) {
                    String sql = stripLeadingBlankAndCommentLines(rawChunk);
                    if (!sql.isEmpty()) {
                        statement.execute(sql);
                    }
                }
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }

    private static String stripLeadingBlankAndCommentLines(String chunk) {
        return Arrays.stream(chunk.split("\\R"))
                .filter(line -> !line.isBlank() && !line.stripLeading().startsWith("--"))
                .collect(Collectors.joining("\n"))
                .trim();
    }

    private long countRows(String table) {
        return jdbc.sql("SELECT COUNT(*) FROM " + table).query(Long.class).single();
    }

    private String fetchSecondPageCursor() {
        JsonNode body = client.get().uri("/api/v1/orders?limit=20")
                .header("X-User-Id", VOLUME_USER_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        assertThat(body).isNotNull();
        String cursor = body.path("nextCursor").asString("");
        assertThat(cursor).isNotBlank();
        return cursor;
    }

    private static MetricResult measure(
            String id, String operation, double p95TargetMs, double p99TargetMs, IntConsumer call) {
        for (int i = -WARMUP_REQUESTS; i < 0; i++) {
            call.accept(i);
        }
        long[] latenciesNanos = new long[MEASURED_REQUESTS];
        for (int i = 0; i < MEASURED_REQUESTS; i++) {
            long start = System.nanoTime();
            call.accept(i);
            latenciesNanos[i] = System.nanoTime() - start;
        }
        Arrays.sort(latenciesNanos);
        return new MetricResult(
                id,
                operation,
                percentileMs(latenciesNanos, 50),
                percentileMs(latenciesNanos, 95),
                percentileMs(latenciesNanos, 99),
                p95TargetMs,
                p99TargetMs);
    }

    static double percentileMs(long[] sortedNanos, int percentile) {
        int rank = (int) Math.ceil((percentile / 100.0) * sortedNanos.length);
        int index = Math.clamp(rank - 1, 0, sortedNanos.length - 1);
        return sortedNanos[index] / 1_000_000.0;
    }

    private static void printReport(
            double loadSeconds,
            long ordersCount,
            long instrumentsCount,
            long marketDataCount,
            long usersCount,
            List<MetricResult> results) {
        System.out.printf(
                Locale.ROOT,
                "%n=== Volume Verification Report (load: %.1fs | orders: %,d | instruments: %,d | marketdata: %,d | users: %,d | warmup: %d | measured: %d) ===%n",
                loadSeconds, ordersCount, instrumentsCount, marketDataCount, usersCount, WARMUP_REQUESTS, MEASURED_REQUESTS);
        System.out.println("| Metric | Operation | p50 (ms) | p95 (ms) | p95 target | p99 (ms) | p99 target | Result |");
        System.out.println("|---|---|---:|---:|---:|---:|---:|---|");
        for (MetricResult r : results) {
            boolean pass = r.p95Ms() < r.p95TargetMs() && r.p99Ms() < r.p99TargetMs();
            System.out.printf(
                    Locale.ROOT,
                    "| %s | %s | %.2f | %.2f | < %.0f ms | %.2f | < %.0f ms | %s |%n",
                    r.id(), r.operation(), r.p50Ms(), r.p95Ms(), r.p95TargetMs(), r.p99Ms(), r.p99TargetMs(),
                    pass ? "PASS" : "FAIL");
        }
        System.out.println();
    }

    private record MetricResult(
            String id,
            String operation,
            double p50Ms,
            double p95Ms,
            double p99Ms,
            double p95TargetMs,
            double p99TargetMs) {
    }
}
