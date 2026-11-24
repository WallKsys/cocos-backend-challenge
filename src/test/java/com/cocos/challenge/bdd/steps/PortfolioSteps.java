package com.cocos.challenge.bdd.steps;

import com.cocos.challenge.bdd.support.ScenarioContext;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PortfolioSteps {

    private static final List<String> POSITION_FIELDS = List.of("ticker", "quantity", "reservedQuantity",
            "lastPrice", "marketValue", "averageCost", "totalReturnPct", "dailyChangePct");

    private final RestTestClient client;
    private final ScenarioContext context;
    private final JdbcClient jdbc;

    public PortfolioSteps(RestTestClient client, ScenarioContext context, JdbcClient jdbc) {
        this.client = client;
        this.context = context;
        this.jdbc = jdbc;
    }

    @Given("user {int} has these filled orders:")
    public void insertFilledOrders(int userId, DataTable orders) {
        for (var row : orders.asMaps()) {
            assertThat(jdbc.sql("""
                    INSERT INTO orders (instrumentid, userid, size, price, type, side, status, datetime,
                                        idempotency_key, request_fingerprint)
                    SELECT id, :userId, :quantity, :price, 'MARKET', :side, 'FILLED', :datetime, NULL, NULL
                    FROM instruments WHERE ticker = :ticker
                    """)
                    .param("userId", userId).param("ticker", row.get("instrument"))
                    .param("quantity", Long.parseLong(row.get("quantity")))
                    .param("price", new BigDecimal(row.get("price"))).param("side", row.get("side"))
                    .param("datetime", OffsetDateTime.parse(row.get("datetime"))).update()).isEqualTo(1);
        }
    }

    @Given("the last price of {string} is {string}")
    public void changeLastPrice(String ticker, String price) {
        assertThat(jdbc.sql("""
                UPDATE marketdata SET close = :price
                WHERE id = (SELECT m.id FROM marketdata m JOIN instruments i ON i.id = m.instrumentid
                            WHERE i.ticker = :ticker ORDER BY m.date DESC LIMIT 1)
                """).param("ticker", ticker).param("price", new BigDecimal(price)).update()).isEqualTo(1);
    }

    @When("user {int} requests their portfolio")
    public void requestPortfolio(int userId) {
        requestUrl(userId, "/api/v1/portfolio");
    }

    @When("user {int} requests the URL {string}")
    public void requestUrl(int userId, String path) {
        context.record(client.get().uri(path).header("X-User-Id", Integer.toString(userId))
                .exchange().expectBody(JsonNode.class).returnResult());
    }

    @When("the administrator requests the portfolio of user {int}")
    public void requestAsAdmin(int userId) {
        context.record(client.get().uri("/api/internal/users/{userId}/portfolio", userId).header("X-Role", "ADMIN")
                .exchange().expectBody(JsonNode.class).returnResult());
    }

    @Then("the portfolio total value is {string}")
    public void totalValue(String expected) {
        assertThat(portfolio().required("totalValue").asString()).isEqualTo(expected);
    }

    @Then("the portfolio cash is {string} with {string} reserved and {string} available")
    public void cash(String total, String reserved, String available) {
        var cash = portfolio().required("cash");
        assertThat(cash.required("total").asString()).isEqualTo(total);
        assertThat(cash.required("reserved").asString()).isEqualTo(reserved);
        assertThat(cash.required("available").asString()).isEqualTo(available);
    }

    @Then("the portfolio positions are:")
    public void positions(DataTable expected) {
        assertThat(portfolio().required("positions").values())
                .extracting(position -> POSITION_FIELDS.stream()
                        .map(position::required).map(value -> value.isNull() ? "-" : value.asText()).toList())
                .containsExactlyElementsOf(expected.cells().subList(1, expected.height()));
    }

    @Then("the position {string} has average cost {string}, total return {string} and daily change {string}")
    public void returns(String ticker, String average, String total, String daily) {
        var position = position(ticker);
        assertThat(position.required("averageCost").asString()).isEqualTo(average);
        assertThat(position.required("totalReturnPct").asString()).isEqualTo(total);
        assertThat(position.required("dailyChangePct").asString()).isEqualTo(daily);
    }

    @Then("the position {string} has quantity {int} and market value {string}")
    public void marketValue(String ticker, int quantity, String value) {
        var position = position(ticker);
        assertThat(position.required("quantity").asLong()).isEqualTo(quantity);
        assertThat(position.required("marketValue").asString()).isEqualTo(value);
    }

    @Then("the position {string} has quantity {int} and no last price, market value, total return or daily change")
    public void unpriced(String ticker, int quantity) {
        var position = position(ticker);
        assertThat(position.required("quantity").asLong()).isEqualTo(quantity);
        for (String field : List.of("lastPrice", "marketValue", "totalReturnPct", "dailyChangePct")) {
            assertThat(position.required(field).isNull()).as(field).isTrue();
        }
    }

    @Then("the position {string} has the warning {string} with message {string}")
    public void positionWarning(String ticker, String code, String message) {
        assertWarning(position(ticker), code, message);
    }

    @Then("the portfolio has the warning {string} with message {string}")
    public void portfolioWarning(String code, String message) {
        assertWarning(portfolio(), code, message);
    }

    @Then("the portfolio has no position {string}")
    public void noPosition(String ticker) {
        assertThat(portfolio().required("positions").values())
                .noneMatch(position -> position.required("ticker").asString().equals(ticker));
    }

    @Then("the portfolio has no positions")
    public void noPositions() {
        assertThat(portfolio().required("positions").values()).isEmpty();
    }

    @Then("the portfolio has no warnings")
    public void noWarnings() {
        assertThat(portfolio().required("warnings").values()).isEmpty();
    }

    private JsonNode portfolio() {
        assertThat(context.status()).isEqualTo(200);
        return context.body();
    }

    private JsonNode position(String ticker) {
        return portfolio().required("positions").values().stream()
                .filter(position -> position.required("ticker").asString().equals(ticker))
                .findFirst().orElseThrow(() -> new AssertionError("Missing position: " + ticker));
    }

    private static void assertWarning(JsonNode owner, String code, String message) {
        assertThat(owner.required("warnings").values()).anySatisfy(warning -> {
            assertThat(warning.required("code").asString()).isEqualTo(code);
            assertThat(warning.required("message").asString()).isEqualTo(message);
        });
    }
}
