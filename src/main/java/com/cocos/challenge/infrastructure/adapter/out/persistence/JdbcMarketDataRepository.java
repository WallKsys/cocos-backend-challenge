package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.MarketDataRepository;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.MarketQuote;
import com.cocos.challenge.domain.model.Money;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class JdbcMarketDataRepository implements MarketDataRepository {

    static final String LATEST_QUOTE_SQL = """
            SELECT close, previousclose, date
            FROM marketdata
            WHERE instrumentid = :instrumentId
            ORDER BY date DESC
            LIMIT 1
            """;

    static final String LATEST_QUOTES_SQL = """
            SELECT DISTINCT ON (instrumentid) instrumentid, close, previousclose
            FROM marketdata
            WHERE instrumentid IN (:instrumentIds)
            ORDER BY instrumentid, date DESC
            """;

    private final JdbcClient jdbc;

    public JdbcMarketDataRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<MarketQuote> latestQuote(InstrumentId instrumentId) {
        return jdbc.sql(LATEST_QUOTE_SQL).param("instrumentId", instrumentId.value())
                .query((rs, rowNum) -> readQuote(rs)).optional();
    }

    @Override
    public Map<InstrumentId, MarketQuote> latestQuotes(Set<InstrumentId> instrumentIds) {
        if (instrumentIds.isEmpty()) {
            return Map.of();
        }
        Map<InstrumentId, MarketQuote> quotes = new HashMap<>();
        jdbc.sql(LATEST_QUOTES_SQL).param("instrumentIds", instrumentIds.stream().map(InstrumentId::value).toList())
                .query(rs -> {
                    quotes.put(new InstrumentId(rs.getLong("instrumentid")), readQuote(rs));
                });
        return quotes;
    }

    private static MarketQuote readQuote(ResultSet rs) throws SQLException {
        BigDecimal previousClose = rs.getBigDecimal("previousclose");
        return new MarketQuote(Money.of(rs.getBigDecimal("close")),
                previousClose == null ? null : Money.of(previousClose));
    }
}
