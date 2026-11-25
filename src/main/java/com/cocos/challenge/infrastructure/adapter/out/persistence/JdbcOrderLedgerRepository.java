package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.domain.model.OrderHistoryCriteria;
import com.cocos.challenge.domain.model.OrderRecord;
import com.cocos.challenge.domain.model.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Optional;

public class JdbcOrderLedgerRepository implements OrderLedgerRepository {

    private final JdbcClient jdbc;

    public JdbcOrderLedgerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<OrderRecord> findRecord(UserId userId, long orderId) {
        throw new UnsupportedOperationException("Order lookup is not implemented");
    }

    @Override
    public boolean cancelIfNew(UserId userId, long orderId) {
        throw new UnsupportedOperationException("Order cancellation is not implemented");
    }

    @Override
    public List<OrderRecord> history(UserId userId, OrderHistoryCriteria criteria, int maxResults) {
        throw new UnsupportedOperationException("Order history is not implemented");
    }
}
