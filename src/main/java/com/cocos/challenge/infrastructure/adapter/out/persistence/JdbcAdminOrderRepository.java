package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.AdminOrderRepository;
import com.cocos.challenge.domain.model.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Optional;

public class JdbcAdminOrderRepository implements AdminOrderRepository {

    private final JdbcClient jdbc;

    public JdbcAdminOrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UserId> findOwner(long orderId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean delete(long orderId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean executeIfNew(long orderId) {
        throw new UnsupportedOperationException();
    }
}
