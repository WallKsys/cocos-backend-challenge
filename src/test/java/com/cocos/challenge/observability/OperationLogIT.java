package com.cocos.challenge.observability;

import com.cocos.challenge.support.IntegrationTest;
import com.cocos.challenge.support.SeedData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 010 (FR-006, SC-002, TEC-27): verifies that every client operation with effect writes
 * one structured INFO {@code Operation completed} log line with the expected attributes and {@code requestId}.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class OperationLogIT {

    @Autowired
    private RestTestClient client;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void resetDatabase() {
        SeedData.reset(jdbc);
    }

    @Test
    void orderPlacementLogsOperationCompleted(CapturedOutput output) {
        long orderId = placeOrder("op-log-buy-1", "op-log-req-1", """
                {"instrumentId":47,"side":"BUY","type":"MARKET","quantity":1}
                """, 201, null);

        JsonNode line = singleOperationLine(output, "op-log-req-1");
        assertCommonFields(line, "PLACE_ORDER", 1L, orderId, "FILLED", false);
        assertThat(line.path("idempotencyKey").asString("")).isEqualTo("op-log-buy-1");
        assertThat(line.has("rejectionReason")).isFalse();
        assertNoForbiddenRequestDetails(line);
    }

    @Test
    void replayedOrderPlacementLogsReplayedTrue(CapturedOutput output) {
        String body = """
                {"instrumentId":47,"side":"BUY","type":"MARKET","quantity":1}
                """;
        long orderId = placeOrder("op-log-buy-2", "op-log-req-2a", body, 201, null);
        long replayedOrderId = placeOrder("op-log-buy-2", "op-log-req-2b", body, 200, "true");
        assertThat(replayedOrderId).isEqualTo(orderId);

        JsonNode line = singleOperationLine(output, "op-log-req-2b");
        assertCommonFields(line, "PLACE_ORDER", 1L, orderId, "FILLED", true);
        assertThat(line.path("idempotencyKey").asString("")).isEqualTo("op-log-buy-2");
        assertThat(line.has("rejectionReason")).isFalse();
    }

    @Test
    void limitOrderAndCancellationLogOperationCompleted(CapturedOutput output) {
        long orderId = placeOrder("op-log-limit-3", "op-log-req-3a", """
                {"instrumentId":47,"side":"BUY","type":"LIMIT","quantity":100,"price":"900.00"}
                """, 201, null);

        JsonNode placedLine = singleOperationLine(output, "op-log-req-3a");
        assertCommonFields(placedLine, "PLACE_ORDER", 1L, orderId, "NEW", false);
        assertThat(placedLine.path("idempotencyKey").asString("")).isEqualTo("op-log-limit-3");
        assertThat(placedLine.has("rejectionReason")).isFalse();

        client.post().uri("/api/v1/orders/{orderId}/cancel", orderId)
                .header("X-User-Id", "1")
                .header("X-Request-Id", "op-log-req-3b")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.orderId").isEqualTo(orderId)
                .jsonPath("$.status").isEqualTo("CANCELLED")
                .jsonPath("$.alreadyCancelled").isEqualTo(false);

        JsonNode cancelledLine = singleOperationLine(output, "op-log-req-3b");
        assertCommonFields(cancelledLine, "CANCEL_ORDER", 1L, orderId, "CANCELLED", false);
        assertThat(cancelledLine.has("idempotencyKey")).isFalse();
        assertThat(cancelledLine.has("rejectionReason")).isFalse();
    }

    @Test
    void cashDepositLogsOperationCompleted(CapturedOutput output) {
        long movementId = cashMovement("/api/v1/cash/deposits", "op-log-dep-4", "op-log-req-4", "100.00");

        JsonNode line = singleOperationLine(output, "op-log-req-4");
        assertCommonFields(line, "DEPOSIT", 1L, movementId, "FILLED", false);
        assertThat(line.path("idempotencyKey").asString("")).isEqualTo("op-log-dep-4");
        assertThat(line.has("rejectionReason")).isFalse();
        assertNoForbiddenRequestDetails(line);
    }

    @Test
    void rejectedCashWithdrawalLogsRejectionReason(CapturedOutput output) {
        long movementId = cashMovement("/api/v1/cash/withdrawals", "op-log-wd-5", "op-log-req-5", "700000.00");

        JsonNode line = singleOperationLine(output, "op-log-req-5");
        assertCommonFields(line, "WITHDRAW", 1L, movementId, "REJECTED", false);
        assertThat(line.path("idempotencyKey").asString("")).isEqualTo("op-log-wd-5");
        assertThat(line.path("rejectionReason").asString("")).isEqualTo("INSUFFICIENT_FUNDS");
        assertNoForbiddenRequestDetails(line);
    }

    @Test
    void errorResponseWritesNoOperationCompletedLine(CapturedOutput output) {
        client.post().uri("/api/v1/orders")
                .header("X-User-Id", "1")
                .header("X-Request-Id", "op-log-req-6")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"instrumentId":47,"side":"BUY","type":"MARKET","quantity":1}
                        """)
                .exchange()
                .expectStatus().isBadRequest();

        assertThat(operationLines(output, "op-log-req-6")).isEmpty();
    }

    private long placeOrder(String idempotencyKey, String requestId, String body,
            int expectedStatus, String expectedReplayedHeader) {
        var spec = client.post().uri("/api/v1/orders")
                .header("X-User-Id", "1")
                .header("Idempotency-Key", idempotencyKey)
                .header("X-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus().isEqualTo(expectedStatus);
        if (expectedReplayedHeader != null) {
            spec.expectHeader().valueEquals("Idempotent-Replayed", expectedReplayedHeader);
        }
        byte[] responseBody = spec.expectBody().returnResult().getResponseBody();
        assertThat(responseBody).isNotNull();
        return jsonMapper.readTree(responseBody).path("orderId").asLong();
    }

    private long cashMovement(String uri, String idempotencyKey, String requestId, String amount) {
        byte[] responseBody = client.post().uri(uri)
                .header("X-User-Id", "1")
                .header("Idempotency-Key", idempotencyKey)
                .header("X-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"amount\":\"" + amount + "\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody().returnResult().getResponseBody();
        assertThat(responseBody).isNotNull();
        return jsonMapper.readTree(responseBody).path("movementId").asLong();
    }

    private JsonNode singleOperationLine(CapturedOutput output, String requestId) {
        List<JsonNode> matches = operationLines(output, requestId);
        assertThat(matches)
                .as("Expected exactly one 'Operation completed' log line for requestId=%s", requestId)
                .hasSize(1);
        return matches.getFirst();
    }

    private List<JsonNode> operationLines(CapturedOutput output, String requestId) {
        return output.getOut().lines()
                .map(String::trim)
                .filter(line -> line.startsWith("{"))
                .map(this::tryParseJson)
                .filter(node -> node != null
                        && "Operation completed".equals(node.path("message").asString(""))
                        && requestId.equals(node.path("requestId").asString("")))
                .toList();
    }

    private JsonNode tryParseJson(String line) {
        try {
            return jsonMapper.readTree(line);
        } catch (JacksonException ignored) {
            return null;
        }
    }

    private static void assertCommonFields(JsonNode line, String operation, long userId, long orderId,
            String result, boolean replayed) {
        assertThat(line.path("log").path("level").asString("")).isEqualTo("INFO");
        assertThat(line.path("operation").asString("")).isEqualTo(operation);
        assertThat(line.path("userId").asString("")).isEqualTo(Long.toString(userId));
        assertThat(line.path("orderId").asString("")).isEqualTo(Long.toString(orderId));
        assertThat(line.path("result").asString("")).isEqualTo(result);
        assertThat(line.path("replayed").asString("")).isEqualTo(Boolean.toString(replayed));
    }

    private static void assertNoForbiddenRequestDetails(JsonNode line) {
        assertThat(line.has("amount")).isFalse();
        assertThat(line.has("price")).isFalse();
        assertThat(line.has("ticker")).isFalse();
        assertThat(line.has("rejectionMessage")).isFalse();
    }
}
