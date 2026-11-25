package com.cocos.challenge.bdd.steps;

import com.cocos.challenge.bdd.support.ScenarioContext;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class InternalToolsSteps {

    private static final List<String> POSITION_FIELDS = List.of("ticker", "quantity", "reservedQuantity",
            "lastPrice", "marketValue", "averageCost", "totalReturnPct", "dailyChangePct");

    private final RestTestClient client;
    private final ScenarioContext context;
    private final JdbcClient jdbc;
    private final PortfolioSteps portfolioSteps;
    private Long deletedOrderId;

    public InternalToolsSteps(RestTestClient client, ScenarioContext context, JdbcClient jdbc,
            PortfolioSteps portfolioSteps) {
        this.client = client;
        this.context = context;
        this.jdbc = jdbc;
        this.portfolioSteps = portfolioSteps;
    }

    @When("a user without the administrator role calls {string} {string}")
    public void callWithoutAdminRole(String method, String path) {
        var spec = client.method(HttpMethod.valueOf(method)).uri(path).header("X-User-Id", "1");
        if (method.equals("PUT") && path.endsWith("/market-data")) {
            spec.contentType(MediaType.APPLICATION_JSON).body(Map.of("close", "1.00"));
        } else if (method.equals("POST") && path.equals("/api/internal/users")) {
            spec.contentType(MediaType.APPLICATION_JSON).body(Map.of("email", "x@test.com"));
        }
        context.record(spec.exchange().returnResult(JsonNode.class));
    }

    @Then("the seed data is unchanged")
    public void seedDataIsUnchanged() {
        assertThat(jdbc.sql("SELECT COUNT(*) FROM orders").query(Integer.class).single()).isEqualTo(11);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM users").query(Integer.class).single()).isEqualTo(4);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM instruments").query(Integer.class).single()).isEqualTo(66);
        assertThat(jdbc.sql("SELECT status FROM orders WHERE id = 7").query(String.class).single()).isEqualTo("NEW");
        assertThat(jdbc.sql("""
                SELECT m.close FROM marketdata m
                JOIN instruments i ON i.id = m.instrumentid
                WHERE i.ticker = 'PAMP' ORDER BY m.date DESC LIMIT 1
                """).query(BigDecimal.class).single()).isEqualByComparingTo("925.85");
    }

    @When("the administrator forces the execution of order {long}")
    public void forceExecution(long orderId) {
        context.record(client.post().uri("/api/internal/orders/{orderId}/execute", orderId)
                .header("X-Role", "ADMIN")
                .exchange().returnResult(JsonNode.class));
    }

    @When("the administrator forces the execution of that order")
    public void forceExecutionOfThatOrder() {
        assertThat(context.status()).isEqualTo(201);
        long orderId = context.body().required("orderId").asLong();
        forceExecution(orderId);
    }

    @Then("the order record has status {string} and price {string}")
    public void orderRecordStatusAndPrice(String status, String price) {
        assertThat(context.body().required("status").asString()).isEqualTo(status);
        JsonNode priceNode = context.body().required("price");
        assertThat(priceNode.isString()).isTrue();
        assertThat(priceNode.asString()).isEqualTo(price);
    }

    @When("the administrator deletes order {long}")
    public void deleteOrder(long orderId) {
        deletedOrderId = orderId;
        context.record(client.delete().uri("/api/internal/orders/{orderId}", orderId)
                .header("X-Role", "ADMIN")
                .exchange().returnResult(JsonNode.class));
    }

    @Given("the administrator deleted that order")
    public void deletedThatOrder() {
        assertThat(context.status()).isEqualTo(201);
        long orderId = context.body().required("orderId").asLong();
        deleteOrder(orderId);
        assertThat(context.status()).isEqualTo(204);
    }

    @Then("order {long} does not exist")
    public void orderDoesNotExist(long orderId) {
        assertThat(jdbc.sql("SELECT COUNT(*) FROM orders WHERE id = :id")
                .param("id", orderId).query(Integer.class).single()).isZero();
    }

    @Then("the order id is different from the deleted order")
    public void orderIdDifferentFromDeleted() {
        assertThat(deletedOrderId).isNotNull();
        assertThat(context.body().required("orderId").asLong()).isNotEqualTo(deletedOrderId);
    }

    @Given("the administrator set the market data of {string} to close {string}")
    public void setMarketDataGiven(String ticker, String close) {
        setMarketData(ticker, close);
        assertThat(context.status()).isEqualTo(200);
    }

    @When("the administrator sets the market data of {string} to close {string}")
    public void setMarketData(String ticker, String close) {
        long instrumentId = jdbc.sql("SELECT id FROM instruments WHERE ticker = :ticker")
                .param("ticker", ticker).query(Long.class).single();
        context.record(client.put().uri("/api/internal/instruments/{instrumentId}/market-data", instrumentId)
                .header("X-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("close", close))
                .exchange().returnResult(JsonNode.class));
    }

    @When("the administrator restores the seed data")
    public void restoreSeedData() {
        context.record(client.post().uri("/api/internal/reset")
                .header("X-Role", "ADMIN")
                .exchange().returnResult(JsonNode.class));
    }

    @When("the administrator creates a user with email {string}")
    public void createUser(String email) {
        context.record(client.post().uri("/api/internal/users")
                .header("X-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email))
                .exchange().returnResult(JsonNode.class));
    }

    @Then("the created user has id {int}, email {string} and account number {string}")
    public void createdUserMatches(int userId, String email, String accountNumber) {
        JsonNode userIdNode = context.body().required("userId");
        assertThat(userIdNode.isIntegralNumber()).isTrue();
        assertThat(userIdNode.asInt()).isEqualTo(userId);
        assertThat(context.body().required("email").asString()).isEqualTo(email);
        assertThat(context.body().required("accountNumber").asString()).isEqualTo(accountNumber);
    }

    @Then("the portfolio of user {int} has cash {string} with {string} reserved and {string} available")
    public void portfolioCash(int userId, String total, String reserved, String available) {
        portfolioSteps.requestAsAdmin(userId);
        portfolioSteps.cash(total, reserved, available);
    }

    @Then("the portfolio of user {int} has total value {string}")
    public void portfolioTotalValue(int userId, String expected) {
        portfolioSteps.requestAsAdmin(userId);
        portfolioSteps.totalValue(expected);
    }

    @Then("the portfolio of user {int} has the warning {string} with message {string}")
    public void portfolioWarning(int userId, String code, String message) {
        portfolioSteps.requestAsAdmin(userId);
        portfolioSteps.portfolioWarning(code, message);
    }

    @Then("the portfolio of user {int} has the position:")
    public void portfolioPosition(int userId, DataTable expected) {
        portfolioSteps.requestAsAdmin(userId);
        assertThat(context.status()).isEqualTo(200);
        assertThat(expected.height()).isEqualTo(2);
        List<String> expectedRow = expected.cells().get(1);
        String ticker = expectedRow.getFirst();
        JsonNode position = context.body().required("positions").values().stream()
                .filter(item -> item.required("ticker").asString().equals(ticker))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing position: " + ticker));
        List<String> actualRow = POSITION_FIELDS.stream()
                .map(position::required)
                .map(value -> value.isNull() ? "-" : value.asText())
                .toList();
        assertThat(actualRow).containsExactlyElementsOf(expectedRow);
    }

    @Then("the portfolio of user {int} has a position {string} with quantity {int} and no last price, market value, total return or daily change")
    public void portfolioUnpricedPosition(int userId, String ticker, int quantity) {
        portfolioSteps.requestAsAdmin(userId);
        portfolioSteps.unpriced(ticker, quantity);
    }
}
