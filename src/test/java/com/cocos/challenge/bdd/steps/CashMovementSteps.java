package com.cocos.challenge.bdd.steps;

import com.cocos.challenge.bdd.support.ScenarioContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class CashMovementSteps {

    private final RestTestClient client;
    private final ScenarioContext context;
    private final JdbcClient jdbc;
    private Request lastRequest;
    private JsonNode originalMovement;

    public CashMovementSteps(RestTestClient client, ScenarioContext context, JdbcClient jdbc) {
        this.client = client;
        this.context = context;
        this.jdbc = jdbc;
    }

    @When("user {int} deposits {string}")
    public void deposit(int userId, String amount) {
        depositWithKey(userId, amount, newKey());
    }

    @Given("user {int} deposited {string} with idempotency key {string}")
    @When("user {int} deposits {string} with idempotency key {string}")
    public void depositWithKey(int userId, String amount, String key) {
        send(new Request(userId, "deposits", key, Map.of("amount", amount)));
    }

    @When("user {int} deposits {string} without idempotency key")
    public void depositWithoutKey(int userId, String amount) {
        depositWithKey(userId, amount, null);
    }

    @When("user {int} withdraws {string}")
    public void withdraw(int userId, String amount) {
        withdrawWithKey(userId, amount, newKey());
    }

    @Given("user {int} withdrew {string} with idempotency key {string}")
    @When("user {int} withdraws {string} with idempotency key {string}")
    public void withdrawWithKey(int userId, String amount, String key) {
        send(new Request(userId, "withdrawals", key, Map.of("amount", amount)));
    }

    @When("user {int} withdraws {string} without idempotency key")
    public void withdrawWithoutKey(int userId, String amount) {
        withdrawWithKey(userId, amount, null);
    }

    @When("^user (\\d+) sends a (deposit|withdrawal) with the raw JSON body:$")
    public void sendRawJson(int userId, String operation, String json) {
        send(new Request(userId, operation + "s", newKey(), json));
    }

    @When("user {int} sends the same cash movement again")
    public void resend(int userId) {
        assertThat(lastRequest.userId()).isEqualTo(userId);
        assertThat(context.status()).isEqualTo(201);
        originalMovement = context.body();
        send(lastRequest);
    }

    @Then("the response is a replay of the original cash movement")
    public void replay() {
        assertThat(context.status()).isEqualTo(200);
        assertThat(context.headers().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(context.body().required("movementId")).isEqualTo(originalMovement.required("movementId"));
        assertThat(context.body()).isEqualTo(originalMovement);
    }

    @Then("the cash movement is created with status {string}")
    public void created(String status) {
        assertThat(context.status()).isEqualTo(201);
        assertThat(context.body().required("status").asText()).isEqualTo(status);
    }

    @Then("the cash movement type is {string} and its amount is {string}")
    public void typeAndAmount(String type, String amount) {
        assertThat(context.body().required("type").asText()).isEqualTo(type);
        JsonNode value = context.body().required("amount");
        assertThat(value.isString()).isTrue();
        assertThat(value.asText()).isEqualTo(amount);
    }

    @Then("the cash movement rejection is {string} with message {string}")
    public void rejection(String code, String message) {
        JsonNode rejection = context.body().required("rejection");
        assertThat(rejection.required("code").asText()).isEqualTo(code);
        assertThat(rejection.required("message").asText()).isEqualTo(message);
    }

    @Then("the stored row of the cash movement has side {string}, size {string} and price {string}")
    public void storedRow(String side, String size, String price) {
        var row = jdbc.sql("SELECT side, size, price FROM orders WHERE id = :id")
                .param("id", context.body().required("movementId").asLong())
                .query((rs, rowNum) -> new StoredMovement(
                        rs.getString("side"), rs.getBigDecimal("size"), rs.getBigDecimal("price")))
                .single();
        assertThat(row.side()).isEqualTo(side);
        assertThat(row.size()).isEqualByComparingTo(size);
        assertThat(row.price()).isEqualByComparingTo(price);
    }

    private void send(Request movement) {
        lastRequest = movement;
        var request = client.post().uri("/api/v1/cash/{operation}", movement.operation())
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", Integer.toString(movement.userId()));
        if (movement.key() != null) {
            request.header("Idempotency-Key", movement.key());
        }
        context.setOrdersBeforeRequest(jdbc.sql("SELECT COUNT(*) FROM orders").query(Long.class).single());
        context.record(request.body(movement.body()).exchange().returnResult(JsonNode.class));
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private record Request(int userId, String operation, String key, Object body) {
    }

    private record StoredMovement(String side, BigDecimal size, BigDecimal price) {
    }
}
