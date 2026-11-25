package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.SeedDataRestorer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class JdbcSeedDataRestorer implements SeedDataRestorer {

    static final String SET_UTC_SQL = "SET LOCAL TIME ZONE 'UTC'";
    static final String TRUNCATE_SQL = "TRUNCATE orders, marketdata, instruments, users RESTART IDENTITY CASCADE";

    private static final List<String> SEED_INSERTS = loadSeedInserts();

    private final JdbcClient jdbc;

    public JdbcSeedDataRestorer(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void restore() {
        jdbc.sql(SET_UTC_SQL).update();
        jdbc.sql(TRUNCATE_SQL).update();
        for (String sql : SEED_INSERTS) {
            jdbc.sql(sql).update();
        }
    }

    private static List<String> loadSeedInserts() {
        try {
            String script = new ClassPathResource("db/migration/V1__original_schema_and_seed.sql")
                    .getContentAsString(StandardCharsets.UTF_8);
            return Arrays.stream(script.split(";"))
                    .map(String::trim)
                    .filter(statement -> statement.startsWith("INSERT"))
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
