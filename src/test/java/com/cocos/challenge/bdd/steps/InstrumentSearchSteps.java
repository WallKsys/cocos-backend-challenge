package com.cocos.challenge.bdd.steps;

import com.cocos.challenge.bdd.support.ScenarioContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class InstrumentSearchSteps {

    private final RestTestClient restTestClient;
    private final ScenarioContext context;
    private final JdbcClient jdbcClient;
    private final List<Integer> pageSizes = new ArrayList<>();
    private final List<String> receivedTickers = new ArrayList<>();

    public InstrumentSearchSteps(RestTestClient restTestClient, ScenarioContext context, JdbcClient jdbcClient) {
        this.restTestClient = restTestClient;
        this.context = context;
        this.jdbcClient = jdbcClient;
    }

    @Given("an instrument with ticker {string} and name {string} exists")
    public void anInstrumentExists(String ticker, String name) {
        jdbcClient.sql("INSERT INTO instruments (ticker, name, type) VALUES (:ticker, :name, 'ACCIONES')")
                .param("ticker", ticker).param("name", name).update();
    }

    @When("I search instruments for {string}")
    public void searchFor(String query) {
        search("/api/v1/instruments?q=" + query);
    }

    @When("I search instruments with ticker filter {string} and name filter {string}")
    public void searchWithTickerAndName(String ticker, String name) {
        search("/api/v1/instruments?ticker=" + ticker + "&name=" + name);
    }

    @When("I search instruments with no criteria")
    public void searchWithoutCriteria() {
        search("/api/v1/instruments");
    }

    @When("I search instruments for {string} with ticker filter {string}")
    public void searchForWithTicker(String query, String ticker) {
        search("/api/v1/instruments?q=" + query + "&ticker=" + ticker);
    }

    @When("I search instruments with name filter {string}")
    public void searchWithName(String name) {
        search("/api/v1/instruments?name=" + name);
    }

    @When("I list all instruments page by page with a page size of {int}")
    public void listAllPages(int limit) {
        pageSizes.clear();
        receivedTickers.clear();
        String cursor = null;
        do {
            search("/api/v1/instruments?limit=" + limit + (cursor == null ? "" : "&cursor=" + cursor));
            pageSizes.add(context.body().get("items").size());
            receivedTickers.addAll(resultTickers());
            JsonNode nextCursor = context.body().get("nextCursor");
            cursor = nextCursor == null || nextCursor.isNull() ? null : nextCursor.asText();
        } while (cursor != null && pageSizes.size() < 10);
    }

    @Then("the result tickers are {string}")
    public void resultTickersAre(String expected) {
        assertThat(resultTickers()).containsExactly(expected.split(", "));
    }

    @Then("the first result ticker is {string}")
    public void firstResultTickerIs(String expected) {
        assertThat(resultTickers()).first().isEqualTo(expected);
    }

    @Then("the result is empty")
    public void resultIsEmpty() {
        assertThat(context.body().get("items")).isEmpty();
    }

    @Then("the result contains {int} instruments")
    public void resultContains(int expected) {
        assertThat(context.body().get("items").size()).isEqualTo(expected);
    }

    @Then("the result tickers start with {string}")
    public void resultTickersStartWith(String expected) {
        assertThat(resultTickers()).startsWith(expected.split(", "));
    }

    @Then("there is a next page")
    public void thereIsANextPage() {
        assertThat(context.body().get("nextCursor").asText()).isNotBlank();
    }

    @Then("the instrument {string} has no last price and no daily change")
    public void instrumentHasNoMarketData(String ticker) {
        JsonNode instrument = instrument(ticker);
        assertThat(instrument.get("lastPrice").isNull()).isTrue();
        assertThat(instrument.get("dailyChangePct").isNull()).isTrue();
    }

    @Then("the instrument {string} has name {string}, type {string}, last price {string} and daily change {string}")
    public void instrumentHasMarketData(String ticker, String name, String type, String price, String dailyChange) {
        JsonNode instrument = instrument(ticker);
        assertThat(instrument.get("name").asText()).isEqualTo(name);
        assertThat(instrument.get("type").asText()).isEqualTo(type);
        assertThat(instrument.get("lastPrice").asText()).isEqualTo(price);
        assertThat(instrument.get("dailyChangePct").asText()).isEqualTo(dailyChange);
    }

    @Then("I receive pages with {int}, {int}, {int} and {int} instruments")
    public void receivePages(int first, int second, int third, int fourth) {
        assertThat(pageSizes).containsExactly(first, second, third, fourth);
    }

    @Then("the instruments received are the {int} tradable instruments in alphabetical order, without repetitions")
    public void receiveWholeMarket(int expectedCount) {
        List<String> expected = jdbcClient.sql(
                        "SELECT ticker FROM instruments WHERE type <> 'MONEDA' ORDER BY ticker COLLATE \"C\"")
                .query(String.class).list();
        assertThat(expected).hasSize(expectedCount);
        assertThat(receivedTickers).isEqualTo(expected);
    }

    private void search(String uri) {
        context.record(restTestClient.get().uri(uri).exchange().returnResult(JsonNode.class));
    }

    private List<String> resultTickers() {
        List<String> tickers = new ArrayList<>();
        for (JsonNode item : context.body().get("items")) {
            tickers.add(item.get("ticker").asText());
        }
        return tickers;
    }

    private JsonNode instrument(String ticker) {
        for (JsonNode item : context.body().get("items")) {
            if (item.get("ticker").asText().equals(ticker)) {
                return item;
            }
        }
        throw new AssertionError("Instrument not found: " + ticker);
    }
}
