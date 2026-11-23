package com.cocos.challenge.support;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class SeedData {

    private SeedData() {
    }

    public static void reset(JdbcClient jdbcClient) {
        jdbcClient.sql("SET LOCAL TIME ZONE 'UTC'").update();
        jdbcClient.sql("TRUNCATE orders, marketdata, instruments, users RESTART IDENTITY CASCADE").update();
        for (String statement : seed().split(";")) {
            String sql = statement.trim();
            if (sql.startsWith("INSERT")) {
                jdbcClient.sql(sql).update();
            }
        }
    }

    private static String seed() {
        try {
            return new ClassPathResource("db/migration/V1__original_schema_and_seed.sql")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
