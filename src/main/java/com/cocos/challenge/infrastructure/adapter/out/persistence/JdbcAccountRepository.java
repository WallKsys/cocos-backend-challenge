package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.AccountRepository;
import com.cocos.challenge.domain.model.Account;
import com.cocos.challenge.domain.model.CreatedUser;
import com.cocos.challenge.domain.model.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class JdbcAccountRepository implements AccountRepository {

    static final String LOCK_SQL = """
            SELECT id, accountnumber FROM users WHERE id = :userId FOR UPDATE
            """;

    static final String FIND_ACCOUNT_SQL = """
            SELECT id, accountnumber FROM users WHERE id = :userId
            """;

    static final String INSERT_SQL = """
            INSERT INTO users (email, accountnumber)
            VALUES (:email, (SELECT (COALESCE(MAX(accountnumber::bigint), 10000) + 1)::text
                             FROM users WHERE accountnumber ~ '^[0-9]+$'))
            RETURNING id, email, accountnumber
            """;

    private final JdbcClient jdbc;

    public JdbcAccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Account> lock(UserId userId) {
        return jdbc.sql(LOCK_SQL).param("userId", userId.value())
                .query(JdbcAccountRepository::mapAccount)
                .optional();
    }

    @Override
    public Optional<Account> find(UserId userId) {
        return jdbc.sql(FIND_ACCOUNT_SQL).param("userId", userId.value())
                .query(JdbcAccountRepository::mapAccount)
                .optional();
    }

    @Override
    public CreatedUser create(String email) {
        return jdbc.sql(INSERT_SQL)
                .param("email", email)
                .query((rs, rowNum) -> new CreatedUser(
                        new UserId(rs.getLong("id")), rs.getString("email"), rs.getString("accountnumber")))
                .single();
    }

    private static Account mapAccount(ResultSet rs, int rowNum) throws SQLException {
        return new Account(new UserId(rs.getLong("id")), rs.getString("accountnumber"));
    }
}
