package com.cocos.challenge.bdd.support;

import com.cocos.challenge.support.SeedData;
import io.cucumber.java.Before;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

public class DatabaseHooks {

    private final JdbcClient jdbcClient;

    public DatabaseHooks(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Before
    @Transactional
    public void resetDatabase() {
        SeedData.reset(jdbcClient);
    }
}
