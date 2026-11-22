package com.cocos.challenge.conventions;

import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

import static com.cocos.challenge.conventions.ProblemResponses.assertProblem;

/** Spec 003 US3 scenario 1: internal routes do not exist unless explicitly enabled (TEC-18). */
@IntegrationTest
@Import(ProbeControllers.class)
class InternalToolsDisabledIT {

    @Autowired
    RestTestClient restTestClient;

    @Test
    void internalEndpointIsNotFoundEvenForAnAdmin() {
        assertProblem(restTestClient.get().uri("/api/internal/probe").header("X-Role", "ADMIN").exchange(),
                404, "NOT_FOUND", "Resource not found.", "/api/internal/probe");
    }
}
