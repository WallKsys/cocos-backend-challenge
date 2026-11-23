package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.InstrumentSearchRepository;
import com.cocos.challenge.domain.model.InstrumentSearchCriteria;
import com.cocos.challenge.domain.model.RankedInstrument;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

public class JdbcInstrumentSearchRepository implements InstrumentSearchRepository {

    private final JdbcClient jdbcClient;

    public JdbcInstrumentSearchRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<RankedInstrument> search(InstrumentSearchCriteria criteria, int maxResults) {
        throw new UnsupportedOperationException();
    }

    String sql(InstrumentSearchCriteria criteria) {
        throw new UnsupportedOperationException();
    }
}
