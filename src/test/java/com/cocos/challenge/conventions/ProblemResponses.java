package com.cocos.challenge.conventions;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Shared RFC 9457 assertions for the convention probes. */
final class ProblemResponses {

    private ProblemResponses() {
    }

    static Problem assertProblem(RestTestClient.ResponseSpec response, int status, String code, String detail,
            String instance) {
        EntityExchangeResult<Problem> result = response.returnResult(Problem.class);
        assertThat(result.getStatus().value()).isEqualTo(status);
        assertThat(result.getResponseHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result.getResponseHeaders().getFirst("X-Request-Id")).isNotBlank();
        Problem problem = result.getResponseBody();
        assertThat(problem).isNotNull();
        assertThat(problem.status()).isEqualTo(status);
        assertThat(problem.code()).isEqualTo(code);
        assertThat(problem.detail()).isEqualTo(detail);
        assertThat(problem.instance()).isEqualTo(instance);
        return problem;
    }

    record Problem(String type, String title, int status, String code, String detail, String instance,
            List<FieldError> errors) {
    }

    record FieldError(String field, String message) {
    }
}
