package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.AccountLock;
import com.cocos.challenge.domain.model.Account;
import com.cocos.challenge.domain.model.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Optional;

public class JdbcAccountLock implements AccountLock {

    private final JdbcClient jdbc;

    public JdbcAccountLock(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Account> lock(UserId userId) {
        throw new UnsupportedOperationException();
    }
}
