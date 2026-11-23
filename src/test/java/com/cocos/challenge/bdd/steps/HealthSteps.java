package com.cocos.challenge.bdd.steps;

import com.cocos.challenge.bdd.support.ScenarioContext;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions of {@code features/000-smoke.feature} (spec 001 US3 scenario 3).
 */
public class HealthSteps {

    private final RestTestClient restTestClient;
    private final ScenarioContext context;

    public HealthSteps(RestTestClient restTestClient, ScenarioContext context) {
        this.restTestClient = restTestClient;
        this.context = context;
    }

    @When("I request the application health")
    public void iRequestTheApplicationHealth() {
        context.record(restTestClient.get().uri("/actuator/health")
                .exchange()
                .returnResult(JsonNode.class));
    }

    @Then("the reported health status is {string}")
    public void theReportedHealthStatusIs(String expectedStatus) {
        assertThat(context.body().get("status").asText()).isEqualTo(expectedStatus);
    }
}
