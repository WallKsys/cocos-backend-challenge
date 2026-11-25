package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.SeedDataRestorer;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcSeedDataRestorer implements SeedDataRestorer {

    private final JdbcClient jdbc;

    public JdbcSeedDataRestorer(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void restore() {
        throw new UnsupportedOperationException();
    }
}
