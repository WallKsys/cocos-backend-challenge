package com.cocos.challenge.support;

import com.cocos.challenge.application.port.out.AccountRepository;
import com.cocos.challenge.domain.model.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** Simulates the spec 009 forced-execution actor for CU-04.08. */
public final class ForcedExecutionSimulator {

    private final TransactionTemplate transaction;
    private final AccountRepository lock;
    private final JdbcClient jdbc;

    public ForcedExecutionSimulator(TransactionTemplate transaction, AccountRepository lock, JdbcClient jdbc) {
        this.transaction = transaction;
        this.lock = lock;
        this.jdbc = jdbc;
    }

    public int execute(UserId userId, long orderId) {
        return transaction.execute(status -> {
            lock.lock(userId).orElseThrow();
            return jdbc.sql("""
                    UPDATE orders SET status = 'FILLED'
                    WHERE id = :orderId AND userid = :userId AND status = 'NEW'
                    """).param("orderId", orderId).param("userId", userId.value()).update();
        });
    }
}
