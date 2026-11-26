package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.domain.model.HistoryCursor;
import com.cocos.challenge.domain.model.InstrumentSearchCriteria;
import com.cocos.challenge.domain.model.OrderHistoryCriteria;
import com.cocos.challenge.domain.model.OrderStatus;
import com.cocos.challenge.domain.model.SearchCursor;
import com.cocos.challenge.domain.model.SearchTerm;
import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class PlanGuardIT {

    private static final Set<String> GUARDED_TABLES = Set.of("orders", "instruments", "marketdata", "users");

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;

    @Autowired
    PlanGuardIT(JdbcClient jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("queries")
    @Transactional
    void criticalQueryUsesIndexWhenSeqScanIsDisabled(QueryCase queryCase) {
        jdbc.sql("SET LOCAL enable_seqscan = off").update();
        String explainJson = jdbc.sql("EXPLAIN (FORMAT JSON) " + queryCase.sql())
                .params(queryCase.params())
                .query(String.class)
                .single();

        JsonNode planRoot = jsonMapper.readTree(explainJson).get(0).required("Plan");
        List<String> seqScans = new ArrayList<>();
        collectSeqScans(planRoot, seqScans);

        assertThat(seqScans)
                .as("Query '%s' performed a sequential scan on guarded tables.\nPlan:\n%s",
                        queryCase.name(), explainJson)
                .isEmpty();
    }

    private static Stream<QueryCase> queries() {
        JdbcOrderLedgerRepository orderLedger = new JdbcOrderLedgerRepository(null);
        JdbcInstrumentRepository search = new JdbcInstrumentRepository(null);
        Instant cursorInstant = Instant.parse("2023-07-13T11:13:20Z");
        OffsetDateTime cursorOffset = cursorInstant.atOffset(ZoneOffset.UTC);

        return Stream.of(
                new QueryCase("account lock", JdbcAccountRepository.LOCK_SQL, Map.of("userId", 1L)),
                new QueryCase("cash balance", JdbcLedgerReader.CASH_BALANCE_SQL, Map.of("userId", 1L)),
                new QueryCase("share balance", JdbcLedgerReader.SHARE_BALANCE_SQL,
                        Map.of("userId", 1L, "instrumentId", 47L)),
                new QueryCase("idempotency record lookup", JdbcOrderRepository.FIND_IDEMPOTENCY_RECORD_SQL,
                        Map.of("userId", 1L, "key", "plan-guard-key")),
                new QueryCase("open positions", JdbcPortfolioReader.OPEN_POSITIONS_SQL, Map.of("userId", 1L)),
                new QueryCase("fills", JdbcPortfolioReader.FILLS_SQL,
                        Map.of("userId", 1L, "instrumentIds", List.of(47L, 31L))),
                new QueryCase("latest quote", JdbcMarketDataRepository.LATEST_QUOTE_SQL,
                        Map.of("instrumentId", 47L)),
                new QueryCase("latest quotes (batch)", JdbcMarketDataRepository.LATEST_QUOTES_SQL,
                        Map.of("instrumentIds", List.of(47L, 31L))),
                new QueryCase("history (no filter)",
                        orderLedger.historySql(new OrderHistoryCriteria(null, 20, null)),
                        Map.of("userId", 1L, "maxResults", 21)),
                new QueryCase("history (status filter)",
                        orderLedger.historySql(new OrderHistoryCriteria(OrderStatus.FILLED, 20, null)),
                        Map.of("userId", 1L, "status", OrderStatus.FILLED.name(), "maxResults", 21)),
                new QueryCase("history (cursor)",
                        orderLedger.historySql(new OrderHistoryCriteria(null, 20, new HistoryCursor(cursorInstant, 7L))),
                        Map.of("userId", 1L, "cursorCreatedAt", cursorOffset, "cursorOrderId", 7L, "maxResults", 21)),
                new QueryCase("single order record", JdbcOrderLedgerRepository.FIND_RECORD_SQL,
                        Map.of("orderId", 7L, "userId", 1L)),
                new QueryCase("search with q long",
                        search.sql(new InstrumentSearchCriteria(new SearchTerm("pampa"), null, null, 20, null)),
                        Map.of("q", "pampa", "qLike", "pampa", "maxResults", 21)),
                new QueryCase("search with q short",
                        search.sql(new InstrumentSearchCriteria(new SearchTerm("gg"), null, null, 20, null)),
                        Map.of("q", "gg", "qLike", "gg", "maxResults", 21)),
                new QueryCase("search with ticker filter (long)",
                        search.sql(new InstrumentSearchCriteria(null, new SearchTerm("pamp"), null, 20, null)),
                        Map.of("ticker", "pamp", "tickerLike", "pamp", "maxResults", 21)),
                new QueryCase("search with ticker filter (short)",
                        search.sql(new InstrumentSearchCriteria(null, new SearchTerm("gg"), null, 20, null)),
                        Map.of("ticker", "gg", "tickerLike", "gg", "maxResults", 21)),
                new QueryCase("search with name filter",
                        search.sql(new InstrumentSearchCriteria(null, null, new SearchTerm("pampa"), 20, null)),
                        Map.of("name", "pampa", "nameLike", "pampa", "maxResults", 21)),
                new QueryCase("search with no criteria and cursor",
                        search.sql(new InstrumentSearchCriteria(null, null, null, 20, new SearchCursor(0, "GGAL"))),
                        Map.of("cursorRank", 0, "cursorTicker", "GGAL", "maxResults", 21))
        );
    }

    private static void collectSeqScans(JsonNode node, List<String> seqScans) {
        String nodeType = node.path("Node Type").asString("");
        String relation = node.path("Relation Name").asString("");
        if ("Seq Scan".equals(nodeType) && GUARDED_TABLES.contains(relation)) {
            seqScans.add(relation);
        }
        JsonNode children = node.path("Plans");
        if (children.isArray()) {
            for (JsonNode child : children) {
                collectSeqScans(child, seqScans);
            }
        }
    }

    private record QueryCase(String name, String sql, Map<String, Object> params) {
        @Override
        public String toString() {
            return name;
        }
    }
}
