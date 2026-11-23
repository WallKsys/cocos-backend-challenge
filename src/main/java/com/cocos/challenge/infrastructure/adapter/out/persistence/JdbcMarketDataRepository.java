package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.MarketDataRepository;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.MarketQuote;
import com.cocos.challenge.domain.model.Money;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.util.Optional;

public class JdbcMarketDataRepository implements MarketDataRepository {

    static final String LATEST_QUOTE_SQL = """
            SELECT close, previousclose, date
            FROM marketdata
            WHERE instrumentid = :instrumentId
            ORDER BY date DESC
            LIMIT 1
            """;

    private final JdbcClient jdbc;

    public JdbcMarketDataRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<MarketQuote> latestQuote(InstrumentId instrumentId) {
        return jdbc.sql(LATEST_QUOTE_SQL).param("instrumentId", instrumentId.value())
                .query((rs, rowNum) -> {
                    BigDecimal previousClose = rs.getBigDecimal("previousclose");
                    return new MarketQuote(Money.of(rs.getBigDecimal("close")),
                            previousClose == null ? null : Money.of(previousClose));
                }).optional();
    }
}
