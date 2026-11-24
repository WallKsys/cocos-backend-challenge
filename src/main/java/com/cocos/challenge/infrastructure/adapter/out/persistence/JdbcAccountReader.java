package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.AccountReader;
import com.cocos.challenge.domain.model.Account;
import com.cocos.challenge.domain.model.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Optional;

public class JdbcAccountReader implements AccountReader {

    private final JdbcClient jdbc;

    public JdbcAccountReader(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Account> find(UserId userId) {
        throw new UnsupportedOperationException();
    }
}
