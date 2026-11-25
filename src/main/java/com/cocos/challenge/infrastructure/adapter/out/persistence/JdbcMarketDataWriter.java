package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.MarketDataWriter;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.Money;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.LocalDate;

public class JdbcMarketDataWriter implements MarketDataWriter {

    private final JdbcClient jdbc;

    public JdbcMarketDataWriter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public StoredMarketData upsert(InstrumentId instrumentId, Money close, Money previousClose, LocalDate date) {
        throw new UnsupportedOperationException();
    }
}
