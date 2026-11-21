package com.cocos.challenge.bdd.steps;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions of {@code features/000-smoke.feature} (spec 001 US3 scenario 3).
 */
public class HealthSteps {

    private final RestTestClient restTestClient;

    private EntityExchangeResult<String> lastResponse;

    public HealthSteps(RestTestClient restTestClient) {
        this.restTestClient = restTestClient;
    }

    @When("I request the application health")
    public void iRequestTheApplicationHealth() {
        lastResponse = restTestClient.get().uri("/actuator/health")
                .exchange()
                .returnResult(String.class);
    }

    @Then("the response status is {int}")
    public void theResponseStatusIs(int expectedStatus) {
        assertThat(lastResponse.getStatus().value()).isEqualTo(expectedStatus);
    }

    @Then("the reported health status is {string}")
    public void theReportedHealthStatusIs(String expectedStatus) {
        assertThat(lastResponse.getResponseBody())
                .contains("\"status\":\"" + expectedStatus + "\"");
    }
}
