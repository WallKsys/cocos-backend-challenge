package com.cocos.challenge.bdd.steps;

import com.cocos.challenge.bdd.support.ScenarioContext;
import io.cucumber.java.en.Then;

import static org.assertj.core.api.Assertions.assertThat;

public class CommonResponseSteps {

    private final ScenarioContext context;

    public CommonResponseSteps(ScenarioContext context) {
        this.context = context;
    }

    @Then("the response status is {int}")
    public void theResponseStatusIs(int expectedStatus) {
        assertThat(context.status()).isEqualTo(expectedStatus);
    }

    @Then("the error code is {string}")
    public void theErrorCodeIs(String expectedCode) {
        assertThat(context.body().get("code").asText()).isEqualTo(expectedCode);
    }

    @Then("the error detail is {string}")
    public void theErrorDetailIs(String expectedDetail) {
        assertThat(context.body().get("detail").asText()).isEqualTo(expectedDetail);
    }
}
