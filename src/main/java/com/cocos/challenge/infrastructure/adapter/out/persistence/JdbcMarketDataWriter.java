package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.MarketDataWriter;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.Money;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;

public class JdbcMarketDataWriter implements MarketDataWriter {

    static final String UPDATE_LATEST_SQL = """
            UPDATE marketdata
            SET close = :close, previousclose = COALESCE(:previousClose, previousclose)
            WHERE id = (SELECT id FROM marketdata WHERE instrumentid = :instrumentId ORDER BY date DESC LIMIT 1)
            RETURNING date, close, previousclose
            """;

    static final String INSERT_SQL = """
            INSERT INTO marketdata (instrumentid, date, close, previousclose)
            VALUES (:instrumentId, :date, :close, :previousClose)
            RETURNING date, close, previousclose
            """;

    private final JdbcClient jdbc;

    public JdbcMarketDataWriter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public StoredMarketData upsert(InstrumentId instrumentId, Money close, Money previousClose, LocalDate date) {
        BigDecimal previousCloseAmount = previousClose == null ? null : previousClose.amount();
        return jdbc.sql(UPDATE_LATEST_SQL)
                .param("instrumentId", instrumentId.value())
                .param("close", close.amount())
                .param("previousClose", previousCloseAmount, Types.NUMERIC)
                .query((rs, rowNum) -> readRow(rs))
                .optional()
                .orElseGet(() -> jdbc.sql(INSERT_SQL)
                        .param("instrumentId", instrumentId.value())
                        .param("date", date)
                        .param("close", close.amount())
                        .param("previousClose", previousCloseAmount, Types.NUMERIC)
                        .query((rs, rowNum) -> readRow(rs))
                        .single());
    }

    private static StoredMarketData readRow(ResultSet rs) throws SQLException {
        BigDecimal previousClose = rs.getBigDecimal("previousclose");
        return new StoredMarketData(rs.getObject("date", LocalDate.class), Money.of(rs.getBigDecimal("close")),
                previousClose == null ? null : Money.of(previousClose));
    }
}
