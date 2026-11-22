package com.cocos.challenge.conventions;

import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 003 US6: correlation ids are echoed when valid, generated otherwise and available in the MDC (TEC-27). */
@IntegrationTest
@Import(ProbeControllers.class)
@ExtendWith(OutputCaptureExtension.class)
class RequestIdIT {

    private static final String VALID_ID = "[A-Za-z0-9._-]{1,64}";

    @Autowired
    RestTestClient restTestClient;

    @Test
    void missingRequestIdIsGenerated() {
        assertThat(log(null)).matches(VALID_ID);
    }

    @Test
    void validRequestIdIsEchoed() {
        assertThat(log("abc-123")).isEqualTo("abc-123");
    }

    @Test
    void invalidRequestIdIsNeverReflected() {
        for (String invalid : List.of("not valid!", "a".repeat(65))) {
            assertThat(log(invalid)).matches(VALID_ID).isNotEqualTo(invalid);
        }
    }

    @Test
    void probeLogLineContainsTheRequestIdFromMdc(CapturedOutput output) {
        assertThat(log("log-123")).isEqualTo("log-123");
        assertThat(output.getOut().lines().filter(line -> line.contains("Conventions log probe")))
                .singleElement().satisfies(line -> assertThat(line).contains("\"requestId\":\"log-123\""));
    }

    private String log(String requestId) {
        RestTestClient.RequestHeadersSpec<?> request = restTestClient.get().uri("/test/conventions/log");
        EntityExchangeResult<Void> result = (requestId == null ? request : request.header("X-Request-Id", requestId))
                .exchange().returnResult(Void.class);
        assertThat(result.getStatus().value()).isEqualTo(204);
        return result.getResponseHeaders().getFirst("X-Request-Id");
    }
}
