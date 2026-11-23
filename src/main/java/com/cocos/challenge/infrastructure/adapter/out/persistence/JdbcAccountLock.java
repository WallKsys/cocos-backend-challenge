package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.AccountLock;
import com.cocos.challenge.domain.model.Account;
import com.cocos.challenge.domain.model.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Optional;

public class JdbcAccountLock implements AccountLock {

    static final String LOCK_SQL = """
            SELECT id, accountnumber FROM users WHERE id = :userId FOR UPDATE
            """;

    private final JdbcClient jdbc;

    public JdbcAccountLock(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Account> lock(UserId userId) {
        return jdbc.sql(LOCK_SQL).param("userId", userId.value())
                .query((rs, rowNum) -> new Account(new UserId(rs.getLong("id")), rs.getString("accountnumber")))
                .optional();
    }
}
