package com.cocos.challenge.infrastructure.adapter.in.rest.internal;

import com.cocos.challenge.support.IntegrationTest;
import com.cocos.challenge.support.SeedData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Tag("integration")
@TestPropertySource(properties = "cocos.internal-tools.enabled=true")
class InternalToolsControllerIT {

    @Autowired
    RestTestClient client;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void resetSeed() {
        SeedData.reset(jdbc);
    }

    @ParameterizedTest
    @Tag("CU-I.01")
    @CsvSource(delimiter = '|', nullValues = "-", value = {
            "DELETE | /api/internal/orders/7 | -",
            "POST   | /api/internal/orders/7/execute | -",
            "PUT    | /api/internal/instruments/47/market-data | {\"close\":\"1000.00\"}",
            "POST   | /api/internal/reset | -",
            "POST   | /api/internal/users | {\"email\":\"x@test.com\"}"
    })
    void withoutAdminRoleToolsDoNotExist(String method, String path, String body) {
        var spec = client.method(HttpMethod.valueOf(method)).uri(path).header("X-User-Id", "1");
        if (body != null) {
            spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        spec.exchange().expectStatus().isNotFound().expectBody()
                .jsonPath("$.code").isEqualTo("NOT_FOUND")
                .jsonPath("$.detail").isEqualTo("Resource not found.");

        assertThat(count("orders")).isEqualTo(11);
        assertThat(count("users")).isEqualTo(4);
        assertThat(jdbc.sql("SELECT status FROM orders WHERE id = 7").query(String.class).single()).isEqualTo("NEW");
        assertThat(jdbc.sql("SELECT close FROM marketdata WHERE instrumentid = 47 ORDER BY date DESC LIMIT 1")
                .query(BigDecimal.class).single()).isEqualByComparingTo("925.85");
    }

    @Test
    void deletesOrderAndRejectsMissingOrder() {
        client.delete().uri("/api/internal/orders/7").header("X-Role", "ADMIN")
                .exchange().expectStatus().isNoContent();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM orders WHERE id = 7").query(Integer.class).single()).isZero();

        client.delete().uri("/api/internal/orders/999999").header("X-Role", "ADMIN")
                .exchange().expectStatus().isNotFound().expectBody()
                .jsonPath("$.code").isEqualTo("NOT_FOUND")
                .jsonPath("$.detail").isEqualTo("Order 999999 was not found.");
    }

    @Test
    void executesPendingLimitOrder() {
        client.post().uri("/api/internal/orders/5/execute").header("X-Role", "ADMIN")
                .exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.orderId").isEqualTo(5)
                .jsonPath("$.instrumentId").isEqualTo(45)
                .jsonPath("$.ticker").isEqualTo("LOMA")
                .jsonPath("$.side").isEqualTo("BUY")
                .jsonPath("$.type").isEqualTo("LIMIT")
                .jsonPath("$.status").isEqualTo("FILLED")
                .jsonPath("$.quantity").isEqualTo(50)
                .jsonPath("$.price").isEqualTo("710.00")
                .jsonPath("$.amount").isEqualTo("35500.00")
                .jsonPath("$.createdAt").value(String.class, value -> assertThat(value).endsWith("Z"))
                .jsonPath("$.rejection").isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "2, 409, ORDER_NOT_EXECUTABLE, Order 2 cannot be executed because its status is FILLED. Only NEW orders can be executed.",
            "3, 409, ORDER_NOT_EXECUTABLE, Order 3 cannot be executed because its status is CANCELLED. Only NEW orders can be executed.",
            "999999, 404, NOT_FOUND, Order 999999 was not found."
    })
    void rejectsNonExecutableOrMissingOrder(long orderId, int status, String code, String detail) {
        client.post().uri("/api/internal/orders/{orderId}/execute", orderId).header("X-Role", "ADMIN")
                .exchange().expectStatus().isEqualTo(status).expectBody()
                .jsonPath("$.code").isEqualTo(code)
                .jsonPath("$.detail").isEqualTo(detail);
    }

    @Test
    void updatesMarketDataAndValidatesInput() {
        client.put().uri("/api/internal/instruments/47/market-data").header("X-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).body("{\"close\":\"1000.00\"}")
                .exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.instrumentId").isEqualTo(47)
                .jsonPath("$.ticker").isEqualTo("PAMP")
                .jsonPath("$.date").isNotEmpty()
                .jsonPath("$.close").isEqualTo("1000.00")
                .jsonPath("$.previousClose").isEqualTo("921.80");

        client.put().uri("/api/internal/instruments/47/market-data").header("X-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).body("{\"close\":\"1000\",\"previousClose\":\"950.5\"}")
                .exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.close").isEqualTo("1000.00")
                .jsonPath("$.previousClose").isEqualTo("950.50");

        client.put().uri("/api/internal/instruments/66/market-data").header("X-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).body("{\"close\":\"1.00\"}")
                .exchange().expectStatus().isBadRequest().expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.detail").isEqualTo(
                        "Request validation failed: 'instrumentId' must reference a tradable instrument; ARS has no market data.");

        client.put().uri("/api/internal/instruments/47/market-data").header("X-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).body("{\"close\":\"0.00\"}")
                .exchange().expectStatus().isBadRequest().expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.detail").isEqualTo("Request validation failed: 'close' must be greater than 0.");

        client.put().uri("/api/internal/instruments/47/market-data").header("X-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).body("{\"close\":\"abc\"}")
                .exchange().expectStatus().isBadRequest().expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.errors[0].field").isEqualTo("close");

        client.put().uri("/api/internal/instruments/999999/market-data").header("X-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).body("{\"close\":\"10.00\"}")
                .exchange().expectStatus().isNotFound().expectBody()
                .jsonPath("$.code").isEqualTo("NOT_FOUND")
                .jsonPath("$.detail").isEqualTo("Instrument 999999 was not found.");
    }

    @Test
    void restoresSeedData() {
        client.delete().uri("/api/internal/orders/7").header("X-Role", "ADMIN")
                .exchange().expectStatus().isNoContent();
        assertThat(count("orders")).isEqualTo(10);

        client.post().uri("/api/internal/reset").header("X-Role", "ADMIN")
                .exchange().expectStatus().isNoContent();
        assertThat(count("orders")).isEqualTo(11);
        assertThat(count("users")).isEqualTo(4);
    }

    @Test
    void createsUserAndRejectsInvalidEmails() {
        client.post().uri("/api/internal/users").header("X-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).body("{\"email\":\"demo@test.com\"}")
                .exchange().expectStatus().isCreated().expectBody()
                .jsonPath("$.userId").isEqualTo(5)
                .jsonPath("$.email").isEqualTo("demo@test.com")
                .jsonPath("$.accountNumber").isEqualTo("10005");

        for (String invalid : new String[]{"{\"email\":\"not-an-email\"}", "{\"email\":\"\"}"}) {
            client.post().uri("/api/internal/users").header("X-Role", "ADMIN")
                    .contentType(MediaType.APPLICATION_JSON).body(invalid)
                    .exchange().expectStatus().isBadRequest().expectBody()
                    .jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "-", value = {
            "DELETE | /api/internal/orders/0                | -                    | orderId",
            "DELETE | /api/internal/orders/-1               | -                    | orderId",
            "POST   | /api/internal/orders/0/execute        | -                    | orderId",
            "POST   | /api/internal/orders/-1/execute       | -                    | orderId",
            "PUT    | /api/internal/instruments/0/market-data  | {\"close\":\"10.00\"} | instrumentId",
            "PUT    | /api/internal/instruments/-1/market-data | {\"close\":\"10.00\"} | instrumentId"
    })
    void rejectsNonPositivePathIds(String method, String path, String body, String param) {
        var spec = client.method(HttpMethod.valueOf(method)).uri(path).header("X-Role", "ADMIN");
        if (body != null) {
            spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        spec.exchange().expectStatus().isBadRequest().expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.detail").isEqualTo("Request validation failed: '" + param + "' must be greater than 0.");
    }

    private int count(String table) {
        return jdbc.sql("SELECT COUNT(*) FROM " + table).query(Integer.class).single();
    }
}
