package com.cocos.challenge.bdd.support;

import com.cocos.challenge.bdd.support.TestClockConfiguration.TestClock;
import com.cocos.challenge.support.SeedData;
import io.cucumber.java.Before;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

public class DatabaseHooks {

    private final JdbcClient jdbcClient;

    private final TestClock clock;

    public DatabaseHooks(JdbcClient jdbcClient, TestClock clock) {
        this.jdbcClient = jdbcClient;
        this.clock = clock;
    }

    @Before
    @Transactional
    public void resetDatabase() {
        clock.reset();
        SeedData.reset(jdbcClient);
    }
}
