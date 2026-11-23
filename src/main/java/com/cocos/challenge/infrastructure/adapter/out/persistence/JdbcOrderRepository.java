package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.IdempotencyRecord;
import com.cocos.challenge.application.port.out.NewOrder;
import com.cocos.challenge.application.port.out.OrderRepository;
import com.cocos.challenge.domain.model.Order;
import com.cocos.challenge.domain.model.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Optional;

public class JdbcOrderRepository implements OrderRepository {

    private final JdbcClient jdbc;

    public JdbcOrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<IdempotencyRecord> findIdempotencyRecord(UserId userId, String key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Order findById(long id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Order save(NewOrder order) {
        throw new UnsupportedOperationException();
    }
}
