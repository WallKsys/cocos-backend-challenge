package com.cocos.challenge.infrastructure.adapter.in.rest.order;

import com.cocos.challenge.application.port.out.LedgerReader;
import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.support.SeedData;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;

abstract class OrderLoadSupport {

    @Autowired
    private RestTestClient client;

    @Autowired
    private LedgerReader ledger;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void resetDatabase() {
        SeedData.reset(jdbc);
    }

    EntityExchangeResult<JsonNode> buy(String key, int quantity) {
        return client.post().uri("/api/v1/orders")
                .header("X-User-Id", "1")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"instrumentId":47,"side":"BUY","type":"MARKET","quantity":%d}
                        """.formatted(quantity))
                .exchange().expectBody(JsonNode.class).returnResult();
    }

    BigDecimal availableCash() {
        return ledger.cashBalance(new UserId(1)).available().amount();
    }

    long orderCount() {
        return jdbc.sql("SELECT COUNT(*) FROM orders").query(Long.class).single();
    }

    static <T> List<T> simultaneously(int count, IntFunction<T> request) throws Exception {
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(count)) {
            List<Future<T>> pending = new ArrayList<>(count);
            try {
                for (int index = 0; index < count; index++) {
                    int requestIndex = index;
                    pending.add(executor.submit(() -> {
                        ready.countDown();
                        assertThat(start.await(10, TimeUnit.SECONDS)).as("start signal").isTrue();
                        return request.apply(requestIndex);
                    }));
                }
                assertThat(ready.await(10, TimeUnit.SECONDS)).as("all request workers ready").isTrue();
            } finally {
                start.countDown();
            }

            List<T> results = new ArrayList<>(count);
            for (var response : pending) {
                results.add(response.get(20, TimeUnit.SECONDS));
            }
            return results;
        }
    }
}
