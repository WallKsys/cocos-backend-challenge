package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.AccountReader;
import com.cocos.challenge.domain.model.Account;
import com.cocos.challenge.domain.model.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Optional;

public class JdbcAccountReader implements AccountReader {

    static final String FIND_ACCOUNT_SQL = """
            SELECT id, accountnumber FROM users WHERE id = :userId
            """;

    private final JdbcClient jdbc;

    public JdbcAccountReader(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Account> find(UserId userId) {
        return jdbc.sql(FIND_ACCOUNT_SQL).param("userId", userId.value())
                .query((rs, rowNum) -> new Account(new UserId(rs.getLong("id")), rs.getString("accountnumber")))
                .optional();
    }
}
