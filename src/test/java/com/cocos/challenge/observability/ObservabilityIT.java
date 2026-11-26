package com.cocos.challenge.observability;

import com.cocos.challenge.support.IntegrationTest;
import com.cocos.challenge.support.SeedData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.grafana.LgtmStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 010 (SC-003, TEC-31): end-to-end verification that metrics, logs, and correlated traces
 * reach the {@code grafana/otel-lgtm} stack.
 */
@IntegrationTest
@Testcontainers
@TestPropertySource(properties = {
        "management.otlp.metrics.export.enabled=true",
        "management.otlp.metrics.export.step=2s",
        "management.tracing.export.otlp.enabled=true",
        "management.logging.export.otlp.enabled=true"
})
@DirtiesContext
class ObservabilityIT {

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(500);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final List<Path> DASHBOARD_FILES = List.of(
            Path.of("observability/grafana/dashboards/cocos-health.json"),
            Path.of("observability/grafana/dashboards/cocos-business.json")
    );

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Container
    @ServiceConnection
    static final LgtmStackContainer lgtm = new LgtmStackContainer("grafana/otel-lgtm:0.41.1");

    @Autowired
    private RestTestClient client;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void resetDatabase() {
        SeedData.reset(jdbc);
    }

    @Test
    void orderPlacementExportsMetricsLogsAndCorrelatedTrace() throws Exception {
        Instant queryStart = Instant.now().minusSeconds(60);

        client.post().uri("/api/v1/orders")
                .header("X-User-Id", "1")
                .header("Idempotency-Key", "obs-it-order-1")
                .header("X-Request-Id", "obs-it-1")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"instrumentId":47,"side":"BUY","type":"MARKET","quantity":1}
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals("X-Request-Id", "obs-it-1")
                .expectBody()
                .jsonPath("$.status").isEqualTo("FILLED")
                .jsonPath("$.ticker").isEqualTo("PAMP");

        verifyMetrics();
        String traceId = verifyLogLineAndExtractTraceId(queryStart);
        verifyTrace(traceId);
    }

    private void verifyMetrics() throws Exception {
        JsonNode orderSeries = pollUntilPresent(
                "Prometheus series cocos_orders_processed_total{side=\"BUY\",type=\"MARKET\",status=\"FILLED\"}",
                () -> queryPrometheusSeries(
                        "cocos_orders_processed_total{side=\"BUY\",type=\"MARKET\",status=\"FILLED\"}"));

        JsonNode metricLabels = orderSeries.path("metric");
        assertThat(metricLabels.propertyNames()).containsExactlyInAnyOrder(
                "__name__", "job", "service_name", "side", "type", "status", "rejection_reason");
        assertThat(metricLabels.path("service_name").asString("")).isEqualTo("cocos-broker-api");
        assertThat(metricLabels.path("rejection_reason").asString("")).isEqualTo("NONE");

        pollUntilPresent(
                "Prometheus series http_server_requests_milliseconds_count{uri=\"/api/v1/orders\"}",
                () -> queryPrometheusSeries("http_server_requests_milliseconds_count{uri=\"/api/v1/orders\"}"));

        pollUntilPresent(
                "Prometheus series hikaricp_connections_active",
                () -> queryPrometheusSeries("hikaricp_connections_active"));

        for (String expr : loadDashboardPromQlExpressions()) {
            JsonNode response = getJson(prometheusQueryUrl(expr));
            assertThat(response.path("status").asString(""))
                    .as("Prometheus query status for dashboard expr: %s", expr)
                    .isEqualTo("success");
        }
    }

    private String verifyLogLineAndExtractTraceId(Instant queryStart) throws Exception {
        String query = "{service_name=\"cocos-broker-api\"} | requestId=\"obs-it-1\"";
        LokiMatch match = pollUntilPresent(
                "Loki log line for service_name=\"cocos-broker-api\" with requestId=\"obs-it-1\" and trace_id",
                () -> queryLokiMatch(query, queryStart),
                () -> lokiDiagnostics(queryStart));

        assertThat(match.serviceName()).isEqualTo("cocos-broker-api");
        assertThat(match.requestId()).isEqualTo("obs-it-1");
        assertThat(match.traceId()).isNotBlank();
        System.out.println("Loki obs-it-1 entry: " + match.rawEntry());
        return match.traceId();
    }

    private void verifyTrace(String traceId) throws Exception {
        JsonNode trace = pollUntilPresent(
                "Tempo trace " + traceId + " with spans for POST /api/v1/orders",
                () -> queryTempoTrace(traceId));

        assertThat(containsOrderRequestSpan(trace))
                .as("Tempo trace %s should contain spans for /api/v1/orders: %s", traceId, trace)
                .isTrue();
    }

    private Optional<JsonNode> queryPrometheusSeries(String expr) {
        try {
            HttpResponse<String> response = get(prometheusQueryUrl(expr));
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            JsonNode root = jsonMapper.readTree(response.body());
            if (!"success".equals(root.path("status").asString(""))) {
                return Optional.empty();
            }
            JsonNode result = root.path("data").path("result");
            return result.isArray() && !result.isEmpty() ? Optional.of(result.get(0)) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<LokiMatch> queryLokiMatch(String logQl, Instant start) {
        try {
            HttpResponse<String> response = get(lokiQueryRangeUrl(logQl, start));
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            JsonNode root = jsonMapper.readTree(response.body());
            if (!"success".equals(root.path("status").asString(""))) {
                return Optional.empty();
            }
            for (JsonNode streamNode : root.path("data").path("result")) {
                JsonNode stream = streamNode.path("stream");
                String serviceName = stream.path("service_name").asString("");
                String requestId = stream.path("requestId").asString("");
                String traceId = stream.path("trace_id").asString("");
                JsonNode values = streamNode.path("values");
                if (values.isArray() && !values.isEmpty() && !traceId.isBlank()) {
                    return Optional.of(new LokiMatch(serviceName, requestId, traceId, streamNode.toString()));
                }
                if (values.isArray()) {
                    for (JsonNode entry : values) {
                        if (entry.isArray() && entry.size() >= 3 && entry.get(2).isObject()) {
                            JsonNode meta = entry.get(2);
                            String metaService = meta.path("service_name").asString(serviceName);
                            String metaRequestId = meta.path("requestId").asString(requestId);
                            String metaTraceId = meta.path("trace_id").asString(traceId);
                            if (!metaTraceId.isBlank()) {
                                return Optional.of(
                                        new LokiMatch(metaService, metaRequestId, metaTraceId, streamNode.toString()));
                            }
                        }
                    }
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<JsonNode> queryTempoTrace(String traceId) {
        try {
            HttpResponse<String> response = get(lgtm.getTempoUrl() + "/api/traces/" + encode(traceId));
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            JsonNode root = jsonMapper.readTree(response.body());
            return containsOrderRequestSpan(root) ? Optional.of(root) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static boolean containsOrderRequestSpan(JsonNode trace) {
        for (JsonNode batch : trace.path("batches")) {
            for (JsonNode scopeSpan : batch.path("scopeSpans")) {
                for (JsonNode span : scopeSpan.path("spans")) {
                    String name = span.path("name").asString("");
                    if (name.contains("/api/v1/orders") || name.contains("POST")) {
                        return true;
                    }
                    for (JsonNode attr : span.path("attributes")) {
                        String key = attr.path("key").asString("");
                        String value = attr.path("value").path("stringValue").asString("");
                        if (("url.path".equals(key) || "http.route".equals(key))
                                && value.contains("/api/v1/orders")) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private List<String> loadDashboardPromQlExpressions() throws IOException {
        List<String> expressions = new ArrayList<>();
        for (Path dashboardPath : DASHBOARD_FILES) {
            JsonNode root = jsonMapper.readTree(Files.readString(dashboardPath));
            collectPromQlFromPanels(root.path("panels"), expressions);
        }
        assertThat(expressions).isNotEmpty();
        return expressions;
    }

    private static void collectPromQlFromPanels(JsonNode panels, List<String> out) {
        if (!panels.isArray()) {
            return;
        }
        for (JsonNode panel : panels) {
            JsonNode targets = panel.path("targets");
            if (targets.isArray()) {
                for (JsonNode target : targets) {
                    if (target.hasNonNull("expr")) {
                        out.add(target.path("expr").asString(""));
                    }
                }
            }
            collectPromQlFromPanels(panel.path("panels"), out);
        }
    }

    private String lokiDiagnostics(Instant start) {
        String lokiAll = safeBody(lokiQueryRangeUrl("{service_name=\"cocos-broker-api\"}", start) + "&limit=20");
        String tempoSearch = safeBody(lgtm.getTempoUrl() + "/api/search?q="
                + encode("{resource.service.name=\"cocos-broker-api\"}"));
        return "Loki {service_name=\"cocos-broker-api\"}: " + truncate(lokiAll, 1500)
                + " | Tempo search: " + truncate(tempoSearch, 1500);
    }

    private String safeBody(String url) {
        try {
            HttpResponse<String> response = get(url);
            return "HTTP " + response.statusCode() + " " + response.body();
        } catch (Exception e) {
            return "ERROR: " + e;
        }
    }

    private static String truncate(String value, int maxLen) {
        return value.length() <= maxLen ? value : value.substring(0, maxLen) + "...";
    }

    private String prometheusQueryUrl(String expr) {
        return lgtm.getPrometheusHttpUrl() + "/api/v1/query?query=" + encode(expr);
    }

    private String lokiQueryRangeUrl(String logQl, Instant start) {
        long startNs = start.getEpochSecond() * 1_000_000_000L;
        long endNs = Instant.now().plusSeconds(60).getEpochSecond() * 1_000_000_000L;
        return lgtm.getLokiUrl() + "/loki/api/v1/query_range?query=" + encode(logQl)
                + "&start=" + startNs + "&end=" + endNs + "&limit=100";
    }

    private JsonNode getJson(String url) throws IOException, InterruptedException {
        HttpResponse<String> response = get(url);
        assertThat(response.statusCode()).as("GET %s -> %s", url, response.body()).isEqualTo(200);
        return jsonMapper.readTree(response.body());
    }

    private static HttpResponse<String> get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static <T> T pollUntilPresent(String description, Supplier<Optional<T>> supplier)
            throws InterruptedException {
        return pollUntilPresent(description, supplier, () -> "");
    }

    private static <T> T pollUntilPresent(String description, Supplier<Optional<T>> supplier,
            Supplier<String> diagnostics) throws InterruptedException {
        Instant deadline = Instant.now().plus(POLL_TIMEOUT);
        while (true) {
            Optional<T> found = supplier.get();
            if (found.isPresent()) {
                return found.get();
            }
            if (Instant.now().isAfter(deadline)) {
                String extra = diagnostics.get();
                throw new AssertionError("Timed out after " + POLL_TIMEOUT + " waiting for " + description
                        + (extra.isBlank() ? "" : ". Diagnostics: " + extra));
            }
            Thread.sleep(POLL_INTERVAL);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record LokiMatch(String serviceName, String requestId, String traceId, String rawEntry) {
    }
}
