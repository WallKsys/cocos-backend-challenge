package com.cocos.challenge;

import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Spec 001 US1 scenario 2: the running application reports itself healthy.
 */
@IntegrationTest
class HealthEndpointIT {

    @Autowired
    RestTestClient restTestClient;

    @Test
    void healthEndpointReportsUp() {
        restTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }
}
