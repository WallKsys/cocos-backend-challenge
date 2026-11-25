package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.UserWriter;
import com.cocos.challenge.domain.model.CreatedUser;
import com.cocos.challenge.domain.model.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcUserWriter implements UserWriter {

    static final String INSERT_SQL = """
            INSERT INTO users (email, accountnumber)
            VALUES (:email, (SELECT (COALESCE(MAX(accountnumber::bigint), 10000) + 1)::text
                             FROM users WHERE accountnumber ~ '^[0-9]+$'))
            RETURNING id, email, accountnumber
            """;

    private final JdbcClient jdbc;

    public JdbcUserWriter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CreatedUser create(String email) {
        return jdbc.sql(INSERT_SQL)
                .param("email", email)
                .query((rs, rowNum) -> new CreatedUser(
                        new UserId(rs.getLong("id")), rs.getString("email"), rs.getString("accountnumber")))
                .single();
    }
}
