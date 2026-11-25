package com.cocos.challenge.observability;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 010 (FR-004, TEC-31): validates Grafana dashboard JSON structure, data sources,
 * low-cardinality tag rules, and byte-identical copies from {@code specs/010-observability/observability/}.
 */
@Tag("unit")
class DashboardsTest {

    private static final Path SPEC_OBSERVABILITY = Path.of("specs/010-observability/observability");
    private static final Path REPO_OBSERVABILITY = Path.of("observability");
    private static final Path DASHBOARDS_DIR = REPO_OBSERVABILITY.resolve("grafana/dashboards");

    private static final List<String> FORBIDDEN_LABEL_TOKENS = List.of(
            "userid", "user_id", "idempotency", "ticker", "orderid", "order_id"
    );

    private static final Pattern PROMQL_BRACES = Pattern.compile("\\{([^}]*)}");
    private static final Pattern PROMQL_GROUPING = Pattern.compile("\\b(?:by|without)\\s*\\(([^)]*)\\)");

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void observabilityArtifactsAreByteIdenticalToSpecCopies() throws IOException {
        assertThat(REPO_OBSERVABILITY).isDirectory();
        List<Path> specFiles = regularRelativePaths(SPEC_OBSERVABILITY);
        List<Path> repoFiles = regularRelativePaths(REPO_OBSERVABILITY);

        assertThat(specFiles).isNotEmpty();
        assertThat(repoFiles).containsExactlyElementsOf(specFiles);

        for (Path relative : specFiles) {
            byte[] expected = Files.readAllBytes(SPEC_OBSERVABILITY.resolve(relative));
            byte[] actual = Files.readAllBytes(REPO_OBSERVABILITY.resolve(relative));
            assertThat(actual)
                    .as("Artifact %s must be byte-identical to spec copy", relative)
                    .isEqualTo(expected);
        }
    }

    @Test
    void healthDashboardHasExpectedUidPrometheusFiltersAndNoForbiddenLabels() throws IOException {
        DashboardStats stats = verifyDashboard(DASHBOARDS_DIR.resolve("cocos-health.json"), "cocos-health");
        assertThat(stats.prometheusTargets()).isGreaterThan(0);
        assertThat(stats.sqlTargets()).isZero();
    }

    @Test
    void businessDashboardHasExpectedUidQueriesAndNoForbiddenLabels() throws IOException {
        DashboardStats stats = verifyDashboard(DASHBOARDS_DIR.resolve("cocos-business.json"), "cocos-business");
        assertThat(stats.prometheusTargets()).isGreaterThan(0);
        assertThat(stats.sqlTargets()).isEqualTo(8);
    }

    private DashboardStats verifyDashboard(Path dashboardPath, String expectedUid) throws IOException {
        assertThat(dashboardPath).isRegularFile();
        JsonNode root = jsonMapper.readTree(Files.readString(dashboardPath));
        assertThat(root.path("uid").asString("")).isEqualTo(expectedUid);

        List<JsonNode> panels = new ArrayList<>();
        collectPanels(root.path("panels"), panels);
        assertThat(panels).isNotEmpty();

        int prometheusTargets = 0;
        int sqlTargets = 0;

        for (JsonNode panel : panels) {
            JsonNode targets = panel.path("targets");
            if (!targets.isArray()) {
                continue;
            }
            String panelTitle = panel.path("title").asString("");
            String panelDatasourceUid = panel.path("datasource").path("uid").asString("");

            for (JsonNode target : targets) {
                String targetDatasourceUid = target.path("datasource").path("uid").asString("");
                String targetDatasourceType = target.path("datasource").path("type").asString("");

                if (target.hasNonNull("expr") || "prometheus".equals(targetDatasourceUid)) {
                    String expr = target.path("expr").asString("");
                    String legendFormat = target.path("legendFormat").asString("");
                    assertThat(expr)
                            .as("Prometheus expr in panel '%s' (%s)", panelTitle, expectedUid)
                            .isNotBlank()
                            .contains("service_name=\"cocos-broker-api\"");
                    assertNoForbiddenPromQlLabels(panelTitle, expr, legendFormat);
                    prometheusTargets++;
                } else if (target.hasNonNull("rawSql")
                        || "grafana-postgresql-datasource".equals(targetDatasourceType)) {
                    String rawSql = target.path("rawSql").asString("");
                    assertThat(rawSql)
                            .as("SQL query in panel '%s' (%s)", panelTitle, expectedUid)
                            .isNotBlank();
                    assertThat(panelDatasourceUid)
                            .as("Panel datasource uid for '%s'", panelTitle)
                            .isEqualTo("cocos-ledger");
                    assertThat(targetDatasourceUid)
                            .as("Target datasource uid for '%s'", panelTitle)
                            .isEqualTo("cocos-ledger");
                    assertNoForbiddenTokens("SQL in panel '" + panelTitle + "'", rawSql);
                    sqlTargets++;
                }
            }
        }
        return new DashboardStats(prometheusTargets, sqlTargets);
    }

    private static void collectPanels(JsonNode panelsNode, List<JsonNode> out) {
        if (!panelsNode.isArray()) {
            return;
        }
        for (JsonNode panel : panelsNode) {
            out.add(panel);
            collectPanels(panel.path("panels"), out);
        }
    }

    private static void assertNoForbiddenPromQlLabels(String panelTitle, String expr, String legendFormat) {
        Matcher matcherMatcher = PROMQL_BRACES.matcher(expr);
        while (matcherMatcher.find()) {
            assertNoForbiddenTokens("PromQL selector in panel '" + panelTitle + "'", matcherMatcher.group(1));
        }
        Matcher groupingMatcher = PROMQL_GROUPING.matcher(expr);
        while (groupingMatcher.find()) {
            assertNoForbiddenTokens("PromQL grouping in panel '" + panelTitle + "'", groupingMatcher.group(1));
        }
        assertNoForbiddenTokens("PromQL legendFormat in panel '" + panelTitle + "'", legendFormat);
    }

    private static void assertNoForbiddenTokens(String context, String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String forbidden : FORBIDDEN_LABEL_TOKENS) {
            assertThat(lower)
                    .as("%s must not reference high-cardinality label '%s'", context, forbidden)
                    .doesNotContain(forbidden);
        }
    }

    private static List<Path> regularRelativePaths(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .map(root::relativize)
                    .sorted()
                    .toList();
        }
    }

    private record DashboardStats(int prometheusTargets, int sqlTargets) {
    }
}
