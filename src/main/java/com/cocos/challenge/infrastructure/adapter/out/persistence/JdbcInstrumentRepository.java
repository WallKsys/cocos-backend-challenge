package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.InstrumentRepository;
import com.cocos.challenge.domain.model.Instrument;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.InstrumentSearchCriteria;
import com.cocos.challenge.domain.model.InstrumentSummary;
import com.cocos.challenge.domain.model.InstrumentType;
import com.cocos.challenge.domain.model.MarketQuote;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.domain.model.SearchTerm;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JdbcInstrumentRepository implements InstrumentRepository {

    static final String FIND_BY_ID_SQL = """
            SELECT id, ticker, type FROM instruments WHERE id = :instrumentId
            """;

    static final String FIND_CASH_INSTRUMENT_SQL = """
            SELECT id FROM instruments WHERE ticker = 'ARS' AND type = 'MONEDA'
            """;

    private static final String BASE_FILTER = "i.type <> 'MONEDA'";
    private static final String Q_SHORT = "i.ticker_normalized = lower(immutable_unaccent(:q))";
    private static final String Q_LONG = """
            (i.ticker_normalized LIKE '%' || lower(immutable_unaccent(:qLike)) || '%' ESCAPE '\\'
             OR i.name_normalized LIKE '%' || lower(immutable_unaccent(:qLike)) || '%' ESCAPE '\\')""";
    private static final String TICKER_SHORT = "i.ticker_normalized = lower(immutable_unaccent(:ticker))";
    private static final String TICKER_LONG =
            "i.ticker_normalized LIKE '%' || lower(immutable_unaccent(:tickerLike)) || '%' ESCAPE '\\'";
    private static final String NAME_FILTER =
            "i.name_normalized LIKE '%' || lower(immutable_unaccent(:nameLike)) || '%' ESCAPE '\\'";
    private static final String RANK = """
            CASE WHEN i.ticker_normalized = lower(immutable_unaccent(:q)) THEN 0
                 WHEN i.ticker_normalized LIKE lower(immutable_unaccent(:qLike)) || '%' ESCAPE '\\' THEN 1
                 ELSE 2 END""";
    private static final String NO_RANK = "0";
    private static final String CURSOR_FILTER =
            "WHERE (c.rank, c.ticker COLLATE \"C\") > (:cursorRank, :cursorTicker COLLATE \"C\")";
    private static final String SEARCH_SQL = """
            SELECT c.id, c.ticker, c.name, c.type, c.rank, md.close, md.previousclose
            FROM (
                SELECT c.*
                FROM (
                    SELECT i.id, i.ticker, i.name, i.type, %s AS rank
                    FROM instruments i
                    WHERE %s
                ) c
                %s
                ORDER BY c.rank, c.ticker COLLATE "C"
                LIMIT :maxResults
            ) c
            LEFT JOIN LATERAL (
                SELECT m.close, m.previousclose
                FROM marketdata m
                WHERE m.instrumentid = c.id
                ORDER BY m.date DESC
                LIMIT 1
            ) md ON TRUE
            ORDER BY c.rank, c.ticker COLLATE "C"
            """;

    private final JdbcClient jdbc;

    public JdbcInstrumentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Instrument> findById(InstrumentId instrumentId) {
        return jdbc.sql(FIND_BY_ID_SQL).param("instrumentId", instrumentId.value())
                .query((rs, rowNum) -> new Instrument(new InstrumentId(rs.getLong("id")),
                        rs.getString("ticker"), InstrumentType.valueOf(rs.getString("type"))))
                .optional();
    }

    @Override
    public Optional<InstrumentId> findCashInstrument() {
        return jdbc.sql(FIND_CASH_INSTRUMENT_SQL).query(Long.class).optional().map(InstrumentId::new);
    }

    @Override
    public List<RankedInstrument> search(InstrumentSearchCriteria criteria, int maxResults) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("maxResults", maxResults);
        addTermParameters(parameters, "q", criteria.q());
        addTermParameters(parameters, "ticker", criteria.ticker());
        addTermParameters(parameters, "name", criteria.name());
        if (criteria.cursor() != null) {
            parameters.put("cursorRank", criteria.cursor().rank());
            parameters.put("cursorTicker", criteria.cursor().ticker());
        }
        return jdbc.sql(sql(criteria)).params(parameters).query((rs, rowNum) -> {
            BigDecimal close = rs.getBigDecimal("close");
            BigDecimal previousClose = rs.getBigDecimal("previousclose");
            MarketQuote quote = close == null ? null : new MarketQuote(
                    Money.of(close), previousClose == null ? null : Money.of(previousClose));
            InstrumentSummary instrument = new InstrumentSummary(new InstrumentId(rs.getLong("id")),
                    rs.getString("ticker"), rs.getString("name"),
                    InstrumentType.valueOf(rs.getString("type")), quote);
            return new RankedInstrument(rs.getInt("rank"), instrument);
        }).list();
    }

    String sql(InstrumentSearchCriteria criteria) {
        String rank = criteria.q() == null ? NO_RANK : RANK;
        StringBuilder filters = new StringBuilder(BASE_FILTER);
        appendFilter(filters, criteria.q(), Q_SHORT, Q_LONG);
        appendFilter(filters, criteria.ticker(), TICKER_SHORT, TICKER_LONG);
        if (criteria.name() != null) {
            filters.append(" AND ").append(NAME_FILTER);
        }
        String cursorFilter = criteria.cursor() == null ? "" : CURSOR_FILTER;
        return SEARCH_SQL.formatted(rank, filters, cursorFilter);
    }

    private static void appendFilter(StringBuilder filters, SearchTerm term, String shortFilter, String longFilter) {
        if (term != null) {
            filters.append(" AND ").append(term.isShort() ? shortFilter : longFilter);
        }
    }

    private static void addTermParameters(
            Map<String, Object> parameters, String parameterName, SearchTerm term) {
        if (term != null) {
            parameters.put(parameterName, term.text());
            parameters.put(parameterName + "Like", escapeLike(term.text()));
        }
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
