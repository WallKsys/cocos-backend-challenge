package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.UserWriter;
import com.cocos.challenge.domain.model.CreatedUser;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcUserWriter implements UserWriter {

    private final JdbcClient jdbc;

    public JdbcUserWriter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CreatedUser create(String email) {
        throw new UnsupportedOperationException();
    }
}
