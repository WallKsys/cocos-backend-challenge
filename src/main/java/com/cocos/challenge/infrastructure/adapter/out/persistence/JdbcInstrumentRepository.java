package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.InstrumentRepository;
import com.cocos.challenge.domain.model.Instrument;
import com.cocos.challenge.domain.model.InstrumentId;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Optional;

public class JdbcInstrumentRepository implements InstrumentRepository {

    private final JdbcClient jdbc;

    public JdbcInstrumentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Instrument> findById(InstrumentId instrumentId) {
        throw new UnsupportedOperationException();
    }
}
