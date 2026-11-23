package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.InstrumentRepository;
import com.cocos.challenge.domain.model.Instrument;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.InstrumentType;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Optional;

public class JdbcInstrumentRepository implements InstrumentRepository {

    static final String FIND_BY_ID_SQL = """
            SELECT id, ticker, type FROM instruments WHERE id = :instrumentId
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
}
