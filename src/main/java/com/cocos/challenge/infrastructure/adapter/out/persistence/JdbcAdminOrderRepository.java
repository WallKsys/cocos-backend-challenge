package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.AdminOrderRepository;
import com.cocos.challenge.domain.model.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Optional;

public class JdbcAdminOrderRepository implements AdminOrderRepository {

    static final String FIND_OWNER_SQL = """
            SELECT userid FROM orders WHERE id = :orderId
            """;

    static final String DELETE_SQL = """
            DELETE FROM orders WHERE id = :orderId
            """;

    static final String EXECUTE_IF_NEW_SQL = """
            UPDATE orders SET status = 'FILLED' WHERE id = :orderId AND status = 'NEW'
            """;

    private final JdbcClient jdbc;

    public JdbcAdminOrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UserId> findOwner(long orderId) {
        return jdbc.sql(FIND_OWNER_SQL)
                .param("orderId", orderId)
                .query((rs, rowNum) -> new UserId(rs.getLong("userid")))
                .optional();
    }

    @Override
    public boolean delete(long orderId) {
        return jdbc.sql(DELETE_SQL).param("orderId", orderId).update() == 1;
    }

    @Override
    public boolean executeIfNew(long orderId) {
        return jdbc.sql(EXECUTE_IF_NEW_SQL).param("orderId", orderId).update() == 1;
    }
}
