package com.cocos.challenge.infrastructure.adapter.in.rest.cash;

import com.cocos.challenge.support.IntegrationTest;
import com.cocos.challenge.support.SeedData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Tag("integration")
class CashMovementControllerIT {

    private static final String DEPOSITS = "/api/v1/cash/deposits";
    private static final String WITHDRAWALS = "/api/v1/cash/withdrawals";
    private static final String AMOUNT = """
            {"amount":"100.00"}
            """;

    @Autowired
    private RestTestClient client;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void resetDatabase() {
        SeedData.reset(jdbc);
    }

    @Test
    @Tag("CU-05.03")
    @Tag("CU-06.08")
    void rejectsExtraDecimalPlaces() {
        rejectsBoth(AMOUNT.replace("100.00", "10.555"), "cash",
                "VALIDATION_ERROR", "Request validation failed: 'amount' must be a decimal string "
                        + "with up to 16 integer digits and at most 2 decimal places.");
    }

    @Test
    @Tag("CU-05.06")
    @Tag("CU-06.05")
    void rejectsUnknownUserIdField() {
        rejectsBoth(AMOUNT.replace("{", "{\"userId\":1,"), "cash", "VALIDATION_ERROR",
                "Request validation failed: 'userId' is not a recognized field.");
    }

    @Test
    @Tag("CU-05.08")
    @Tag("CU-06.09")
    void requiresIdempotencyKey() {
        rejectsBoth(AMOUNT, null, "MISSING_IDEMPOTENCY_KEY",
                "The 'Idempotency-Key' header is required for this operation.");
    }

    @Test
    @Tag("CU-05.07")
    void depositKeyCannotBeReusedForWithdrawal() {
        long before = orderCount();
        post(DEPOSITS, 2, "shared", AMOUNT).expectStatus().isCreated().expectBody()
                .jsonPath("$.movementId").isNumber()
                .jsonPath("$.type").isEqualTo("CASH_IN")
                .jsonPath("$.status").isEqualTo("FILLED")
                .jsonPath("$.amount").isEqualTo("100.00")
                .jsonPath("$.createdAt").value(String.class, value -> assertThat(value).endsWith("Z"))
                .jsonPath("$.rejection").hasJsonPath()
                .jsonPath("$.rejection").isEmpty();
        post(WITHDRAWALS, 2, "shared", AMOUNT).expectStatus().isEqualTo(422).expectBody()
                .jsonPath("$.code").isEqualTo("IDEMPOTENCY_CONFLICT")
                .jsonPath("$.detail").isEqualTo("Idempotency key 'shared' was already used for a different request.");
        assertThat(orderCount()).isEqualTo(before + 1);
    }

    @Test
    void unknownUserCannotMoveCash() {
        long before = orderCount();
        for (String path : new String[]{DEPOSITS, WITHDRAWALS}) {
            post(path, 99999, "unknown-user", AMOUNT).expectStatus().isNotFound().expectBody()
                    .jsonPath("$.code").isEqualTo("NOT_FOUND")
                    .jsonPath("$.detail").isEqualTo("User 99999 was not found.");
        }
        assertThat(orderCount()).isEqualTo(before);
    }

    private void rejectsBoth(String body, String key, String code, String detail) {
        long before = orderCount();
        for (String path : new String[]{DEPOSITS, WITHDRAWALS}) {
            post(path, 2, key, body).expectStatus().isBadRequest().expectBody()
                    .jsonPath("$.code").isEqualTo(code)
                    .jsonPath("$.detail").isEqualTo(detail);
            assertThat(orderCount()).isEqualTo(before);
        }
    }

    private RestTestClient.ResponseSpec post(String path, int userId, String key, String body) {
        var request = client.post().uri(path).contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", Integer.toString(userId));
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return request.body(body).exchange();
    }

    private long orderCount() {
        return jdbc.sql("SELECT COUNT(*) FROM orders").query(Long.class).single();
    }
}
