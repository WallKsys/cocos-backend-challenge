package com.cocos.challenge.conventions;

import com.cocos.challenge.conventions.ProblemResponses.Problem;
import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static com.cocos.challenge.conventions.ProblemResponses.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec 003 US4: the idempotency key is required, visible ASCII and never trimmed. */
@IntegrationTest
@Import(ProbeControllers.class)
class IdempotencyKeyIT {

    @Autowired
    RestTestClient restTestClient;

    @LocalServerPort
    int port;

    @Test
    void validKeyReachesTheEndpointWithoutAReplayHeader() {
        EntityExchangeResult<KeyResponse> result = idempotent("k-1").returnResult(KeyResponse.class);
        assertThat(result.getStatus().value()).isEqualTo(201);
        assertThat(result.getResponseBody()).isEqualTo(new KeyResponse("k-1"));
        assertThat(result.getResponseHeaders().getFirst("Idempotent-Replayed")).isNull();
    }

    @Test
    void missingOrBlankKeyHasItsOwnError() {
        for (String key : Arrays.asList(null, "", "  ")) {
            Problem problem = assertProblem(idempotent(key), 400, "MISSING_IDEMPOTENCY_KEY",
                    "The 'Idempotency-Key' header is required for this operation.", "/test/conventions/idempotent");
            assertThat(problem.type()).isEqualTo("https://api.cocos.example/errors/missing-idempotency-key");
            assertThat(problem.title()).isEqualTo("Missing idempotency key");
        }
    }

    @Test
    void invalidKeyIsRejectedWithoutBeingTrimmed() {
        for (String key : List.of("a".repeat(256), "has space")) {
            assertProblem(idempotent(key), 400, "VALIDATION_ERROR",
                    "The 'Idempotency-Key' header must be 1 to 255 visible ASCII characters.",
                    "/test/conventions/idempotent");
        }
    }

    @Test
    void surroundingWhitespaceIsRemovedByTheHttpServer() {
        EntityExchangeResult<KeyResponse> result = idempotent(" k-1 ").returnResult(KeyResponse.class);
        assertThat(result.getStatus().value()).isEqualTo(201);
        assertThat(result.getResponseBody()).isEqualTo(new KeyResponse("k-1"));
    }

    @Test
    void nonAsciiKeyIsRejectedOverRawHttp() throws IOException {
        String response;
        try (Socket socket = new Socket("localhost", port)) {
            socket.getOutputStream().write(
                    "POST /test/conventions/idempotent HTTP/1.1\r\nHost: localhost\r\nIdempotency-Key: cl"
                            .getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().write(new byte[]{(byte) 0xC3, (byte) 0xA9});
            socket.getOutputStream().write("\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(response).startsWith("HTTP/1.1 400");
        assertThat(response).contains(
                "\"detail\":\"The 'Idempotency-Key' header must be 1 to 255 visible ASCII characters.\"");
    }

    @Test
    void replayedResponseHasTheReplayHeader() {
        EntityExchangeResult<KeyResponse> result = restTestClient.post().uri("/test/conventions/replayed")
                .exchange().returnResult(KeyResponse.class);
        assertThat(result.getStatus().value()).isEqualTo(200);
        assertThat(result.getResponseHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
    }

    private RestTestClient.ResponseSpec idempotent(String key) {
        RestTestClient.RequestBodySpec request = restTestClient.post().uri("/test/conventions/idempotent");
        return (key == null ? request : request.header("Idempotency-Key", key)).exchange();
    }

    private record KeyResponse(String key) {
    }
}
