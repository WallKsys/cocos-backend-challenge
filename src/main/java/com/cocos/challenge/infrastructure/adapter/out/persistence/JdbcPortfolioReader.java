package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.OpenPosition;
import com.cocos.challenge.application.port.out.PortfolioReader;
import com.cocos.challenge.domain.model.Fill;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class JdbcPortfolioReader implements PortfolioReader {

    private final JdbcClient jdbc;

    public JdbcPortfolioReader(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<OpenPosition> openPositions(UserId userId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<InstrumentId, List<Fill>> fills(UserId userId, Set<InstrumentId> instrumentIds) {
        throw new UnsupportedOperationException();
    }
}
