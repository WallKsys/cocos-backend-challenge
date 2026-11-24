package com.cocos.challenge.infrastructure.adapter.in.rest.cash;

import com.cocos.challenge.support.IntegrationTest;
import com.cocos.challenge.support.SeedData;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Tag("integration")
class CashIdempotencyIT {

    @Autowired
    private RestTestClient client;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void simultaneousIdenticalDepositsCreateOneMovement() throws Exception {
        SeedData.reset(jdbc);
        long before = orderCount();
        var responses = depositConcurrently();
        var created = responses.stream().filter(response -> response.getStatus().value() == 201).toList();
        assertThat(created).hasSize(1);
        var original = created.getFirst().getResponseBody();
        long movementId = original.required("movementId").asLong();
        assertThat(responses).allSatisfy(response -> {
            assertThat(response.getResponseBody()).isEqualTo(original);
            if (response != created.getFirst()) {
                assertThat(response.getStatus().value()).isEqualTo(200);
                assertThat(response.getResponseHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
            }
        });
        assertThat(orderCount()).isEqualTo(before + 1);
        assertThat(jdbc.sql("SELECT id FROM orders WHERE userid = 2 AND idempotency_key = 'same-deposit'")
                .query(Long.class).single()).isEqualTo(movementId);
    }

    private List<EntityExchangeResult<JsonNode>> depositConcurrently() throws Exception {
        int requests = 50;
        var ready = new CountDownLatch(requests);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var pending = new ArrayList<Future<EntityExchangeResult<JsonNode>>>(requests);
            for (int i = 0; i < requests; i++) {
                pending.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return client.post().uri("/api/v1/cash/deposits").contentType(MediaType.APPLICATION_JSON)
                            .header("X-User-Id", "2").header("Idempotency-Key", "same-deposit")
                            .body("{\"amount\":\"1234.56\"}").exchange().returnResult(JsonNode.class);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var responses = new ArrayList<EntityExchangeResult<JsonNode>>(requests);
            for (var response : pending) {
                responses.add(response.get(20, TimeUnit.SECONDS));
            }
            return responses;
        }
    }

    private long orderCount() {
        return jdbc.sql("SELECT COUNT(*) FROM orders").query(Long.class).single();
    }
}
