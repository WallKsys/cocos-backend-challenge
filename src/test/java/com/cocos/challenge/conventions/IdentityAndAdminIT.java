package com.cocos.challenge.conventions;

import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.cocos.challenge.conventions.ProblemResponses.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec 003 US2 and US3 scenarios 2–3 (TEC-18, DEC-17). */
@IntegrationTest
@Import(ProbeControllers.class)
@TestPropertySource(properties = "cocos.internal-tools.enabled=true")
class IdentityAndAdminIT {

    @Autowired
    RestTestClient restTestClient;

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
    void adminCanCallAnEnabledInternalEndpoint() {
        assertThat(internal("ADMIN").returnResult(Void.class).getStatus().value()).isEqualTo(204);
    }

    @Test
    void internalEndpointRequiresTheExactAdminRole() {
        for (String role : Arrays.asList(null, "admin", "USER")) {
            assertProblem(internal(role), 404, "NOT_FOUND", "Resource not found.", "/api/internal/probe");
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
