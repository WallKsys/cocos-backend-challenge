package com.cocos.challenge.conventions;

import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 003 US5 scenarios 2 and 5 (JSON coercion): JSON numbers and booleans are rejected for textual fields,
 * and decimals or strings are rejected for integer fields. Slice 003-d adds the remaining US5 scenarios.
 */
@IntegrationTest
@Import(ProbeControllers.class)
class FormatsIT {

    @Autowired
    RestTestClient restTestClient;

    @Test
    void decimalQuantityIsRejectedInsteadOfTruncated() {
        assertInvalidType("{\"amount\":\"1.00\",\"quantity\":1.5}", "quantity");
    }

    @Test
    void decimalQuantityWithZeroFractionIsRejected() {
        assertInvalidType("{\"amount\":\"1.00\",\"quantity\":10.0}", "quantity");
    }

    @Test
    void stringQuantityIsRejected() {
        assertInvalidType("{\"amount\":\"1.00\",\"quantity\":\"10\"}", "quantity");
    }

    @Test
    void numericAmountIsRejectedInsteadOfCoerced() {
        assertInvalidType("{\"amount\":925.85,\"quantity\":1}", "amount");
    }

    private void assertInvalidType(String body, String field) {
        EntityExchangeResult<Problem> result = restTestClient.post().uri("/test/conventions/validate")
                .contentType(MediaType.APPLICATION_JSON).body(body).exchange().returnResult(Problem.class);
        assertThat(result.getStatus().value()).isEqualTo(400);
        assertThat(result.getResponseHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        Problem problem = result.getResponseBody();
        assertThat(problem).isNotNull();
        assertThat(problem.type()).isEqualTo("https://api.cocos.example/errors/validation-error");
        assertThat(problem.title()).isEqualTo("Validation error");
        assertThat(problem.status()).isEqualTo(400);
        assertThat(problem.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(problem.detail())
                .isEqualTo("Request validation failed: '" + field + "' has an invalid type or format.");
        assertThat(problem.instance()).isEqualTo("/test/conventions/validate");
        assertThat(problem.errors()).containsExactly(new FieldError(field, "has an invalid type or format"));
    }

    private record Problem(String type, String title, int status, String code, String detail, String instance,
            List<FieldError> errors) {
    }

    private record FieldError(String field, String message) {
    }
}
