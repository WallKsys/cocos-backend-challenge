package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.MarketDataRepository;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.MarketQuote;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Optional;

public class JdbcMarketDataRepository implements MarketDataRepository {

    private final JdbcClient jdbc;

    public JdbcMarketDataRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<MarketQuote> latestQuote(InstrumentId instrumentId) {
        throw new UnsupportedOperationException();
    }
}
