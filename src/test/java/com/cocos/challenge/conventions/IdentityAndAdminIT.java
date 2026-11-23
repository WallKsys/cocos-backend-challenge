package com.cocos.challenge.conventions;

import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.cocos.challenge.conventions.ProblemResponses.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec 003 US2 scenarios 1–3 and US3 scenarios 2–4 (TEC-18, DEC-17). */
@IntegrationTest
@Import(ProbeControllers.class)
@TestPropertySource(properties = "cocos.internal-tools.enabled=true")
class IdentityAndAdminIT {

    @Autowired
    RestTestClient restTestClient;

    @LocalServerPort
    int port;

    @Test
    void userIdComesFromTheTrimmedHeader() {
        for (String header : List.of("1", " 1 ")) {
            EntityExchangeResult<Map> result = user(header).returnResult(Map.class);
            assertThat(result.getStatus().value()).isEqualTo(200);
            assertThat(result.getResponseBody()).containsExactly(Map.entry("userId", 1));
        }
    }

    @Test
    void missingOrBlankUserIdIsRejected() {
        for (String header : Arrays.asList(null, "", "  ")) {
            assertProblem(user(header), 400, "VALIDATION_ERROR", "The 'X-User-Id' header is required.",
                    "/test/conventions/user");
        }
    }

    @Test
    void invalidUserIdIsRejected() {
        for (String header : List.of("abc", "0", "-5", "1.0", "2147483648")) {
            assertProblem(user(header), 400, "VALIDATION_ERROR",
                    "The 'X-User-Id' header must be a positive integer.", "/test/conventions/user");
        }
    }

    @Test
    void userIdsBeyondTheSixtyFourBitRangeAreRejected() {
        for (String header : List.of("9223372036854775808", "99999999999999999999")) {
            assertProblem(user(header), 400, "VALIDATION_ERROR",
                    "The 'X-User-Id' header must be a positive integer.", "/test/conventions/user");
        }
    }

    @Test
    void repeatedUserIdHeadersAreRejectedOverRawHttp() throws IOException {
        String response;
        try (Socket socket = new Socket("localhost", port)) {
            socket.getOutputStream().write(("GET /test/conventions/user HTTP/1.1\r\nHost: localhost\r\n"
                    + "X-User-Id: 1\r\nX-User-Id: 1\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(response).startsWith("HTTP/1.1 400");
        assertThat(response).contains("\"detail\":\"The 'X-User-Id' header must be sent only once.\"");
    }

    @Test
    void adminCanCallAnEnabledInternalEndpoint() {
        assertThat(internal("ADMIN").returnResult(Void.class).getStatus().value()).isEqualTo(204);
    }

    @Test
    void internalEndpointRequiresTheExactAdminRole() {
        for (String role : Arrays.asList(null, "admin", "USER")) {
            assertProblem(internal(role), 404, "NOT_FOUND", "Resource not found.", "/api/internal/probe");
        }
    }

    @Test
    void unsupportedMethodsDoNotRevealInternalRoutesWithoutTheAdminRole() {
        for (HttpMethod method : List.of(HttpMethod.POST, HttpMethod.DELETE)) {
            for (String role : Arrays.asList(null, "admin")) {
                RestTestClient.RequestHeadersSpec<?> request = restTestClient.method(method)
                        .uri("/api/internal/probe");
                RestTestClient.ResponseSpec response = (role == null ? request : request.header("X-Role", role))
                        .exchange();
                assertProblem(response, 404, "NOT_FOUND", "Resource not found.", "/api/internal/probe");
                response.expectHeader().doesNotExist("Allow");
            }
        }
    }

    private RestTestClient.ResponseSpec user(String userId) {
        RestTestClient.RequestHeadersSpec<?> request = restTestClient.get().uri("/test/conventions/user");
        return (userId == null ? request : request.header("X-User-Id", userId)).exchange();
    }

    private RestTestClient.ResponseSpec internal(String role) {
        RestTestClient.RequestHeadersSpec<?> request = restTestClient.get().uri("/api/internal/probe");
        return (role == null ? request : request.header("X-Role", role)).exchange();
    }
}
