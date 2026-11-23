package com.cocos.challenge.contract;

import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class PublishedContractEndpointsIT {

    @Autowired
    RestTestClient restTestClient;

    @Test
    void publishedContractIsServedAsYaml() {
        EntityExchangeResult<String> result = restTestClient.get().uri("/openapi/openapi.yaml")
                .exchange().returnResult(String.class);
        assertThat(result.getStatus().value()).isEqualTo(200);
        assertThat(result.getResponseBody()).contains("openapi: 3.1.0");
    }

    @Test
    void swaggerUiIsServed() {
        EntityExchangeResult<String> result = restTestClient.get().uri("/swagger-ui/index.html")
                .exchange().returnResult(String.class);
        assertThat(result.getStatus().value()).isEqualTo(200);
    }
}
