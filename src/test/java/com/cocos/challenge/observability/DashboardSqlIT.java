package com.cocos.challenge.observability;

import com.cocos.challenge.support.IntegrationTest;
import com.cocos.challenge.support.SeedData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 010 (FR-004, TEC-31): verifies every historical SQL query in {@code cocos-business.json}
 * runs cleanly against PostgreSQL and matches the expected seed values.
 */
@IntegrationTest
@Transactional
class DashboardSqlIT {

    private static final Path BUSINESS_DASHBOARD =
            Path.of("observability/grafana/dashboards/cocos-business.json");

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;

    @Autowired
    DashboardSqlIT(JdbcClient jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    @BeforeEach
    void resetDatabase() {
        SeedData.reset(jdbc);
    }

    @Test
    void everySqlQueryInBusinessDashboardExecutesWithoutError() throws IOException {
        Map<String, String> sqlByTitle = loadSqlByPanelTitle();
        assertThat(sqlByTitle).hasSize(8);

        for (Map.Entry<String, String> entry : sqlByTitle.entrySet()) {
            List<Map<String, Object>> rows = jdbc.sql(entry.getValue()).query().listOfRows();
            assertThat(rows)
                    .as("Query for panel '%s' returned rows on seed data", entry.getKey())
                    .isNotEmpty();
        }
    }

    @Test
    void historicalDashboardQueriesReturnExpectedSeedValues() throws IOException {
        Map<String, String> sqlByTitle = loadSqlByPanelTitle();

        Long totalTransactions = jdbc.sql(sqlByTitle.get("Total transactions"))
                .query(Long.class)
                .single();
        assertThat(totalTransactions).isEqualTo(11L);

        BigDecimal successRate = jdbc.sql(sqlByTitle.get("Success rate of trades"))
                .query(BigDecimal.class)
                .single();
        assertThat(successRate).isEqualByComparingTo("55.56");

        BigDecimal rejectionRate = jdbc.sql(sqlByTitle.get("Rejection rate of trades"))
                .query(BigDecimal.class)
                .single();
        assertThat(rejectionRate).isEqualByComparingTo("11.11");

        Map<String, Long> byStatus = toLongMap(sqlByTitle.get("Orders by status"), "status", "orders");
        assertThat(byStatus).containsExactly(
                Map.entry("CANCELLED", 1L),
                Map.entry("FILLED", 7L),
                Map.entry("NEW", 2L),
                Map.entry("REJECTED", 1L)
        );

        Map<String, Long> rejectionsByReason =
                toLongMap(sqlByTitle.get("Rejections by reason"), "reason", "rejections");
        assertThat(rejectionsByReason).containsExactly(Map.entry("NOT_RECORDED", 1L));

        Map<String, BigDecimal> volumeBySide =
                toDecimalMap(sqlByTitle.get("Traded volume by side (ARS)"), "side", "volume");
        assertThat(volumeBySide).containsOnlyKeys("BUY", "SELL");
        assertThat(volumeBySide.get("BUY")).isEqualByComparingTo("202300.00");
        assertThat(volumeBySide.get("SELL")).isEqualByComparingTo("55300.00");

        Map<String, BigDecimal> cashMovements =
                toDecimalMap(sqlByTitle.get("Deposits and withdrawals (ARS)"), "side", "amount");
        assertThat(cashMovements).containsOnlyKeys("CASH_IN", "CASH_OUT");
        assertThat(cashMovements.get("CASH_IN")).isEqualByComparingTo("1000000.00");
        assertThat(cashMovements.get("CASH_OUT")).isEqualByComparingTo("100000.00");

        List<Map<String, Object>> ordersPerDay = jdbc.sql(sqlByTitle.get("Orders per day"))
                .query()
                .listOfRows();
        assertThat(ordersPerDay).hasSize(2);
        long summedDailyOrders = ordersPerDay.stream()
                .mapToLong(row -> ((Number) row.get("orders")).longValue())
                .sum();
        assertThat(summedDailyOrders).isEqualTo(11L);
    }

    private Map<String, String> loadSqlByPanelTitle() throws IOException {
        JsonNode root = jsonMapper.readTree(Files.readString(BUSINESS_DASHBOARD));
        Map<String, String> sqlByTitle = new LinkedHashMap<>();
        for (JsonNode panel : root.path("panels")) {
            String title = panel.path("title").asString("");
            JsonNode targets = panel.path("targets");
            if (!targets.isArray()) {
                continue;
            }
            for (JsonNode target : targets) {
                if (target.hasNonNull("rawSql")) {
                    String sql = target.path("rawSql").asString("")
                            .replace("$__timeFilter(datetime)", "TRUE");
                    sqlByTitle.put(title, sql);
                }
            }
        }
        return sqlByTitle;
    }

    private Map<String, Long> toLongMap(String sql, String keyColumn, String valueColumn) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.sql(sql).query().listOfRows()) {
            result.put((String) row.get(keyColumn), ((Number) row.get(valueColumn)).longValue());
        }
        return result;
    }

    private Map<String, BigDecimal> toDecimalMap(String sql, String keyColumn, String valueColumn) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.sql(sql).query().listOfRows()) {
            result.put((String) row.get(keyColumn), (BigDecimal) row.get(valueColumn));
        }
        return result;
    }
}
