package com.cocos.challenge.bdd.steps;

import com.cocos.challenge.application.port.out.LedgerReader;
import com.cocos.challenge.bdd.support.ScenarioContext;
import com.cocos.challenge.bdd.support.TestClockConfiguration.TestClock;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.domain.model.RequestFingerprint;
import com.cocos.challenge.domain.model.UserId;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class OrderSteps {

    private final RestTestClient client;
    private final ScenarioContext context;
    private final JdbcClient jdbc;
    private final LedgerReader ledger;
    private final TestClock clock;
    private Request lastRequest;
    private JsonNode originalOrder;
    private long ordersBeforeRequest;
    private final List<EntityExchangeResult<JsonNode>> concurrentResponses = new ArrayList<>();

    public OrderSteps(RestTestClient client, ScenarioContext context, JdbcClient jdbc, LedgerReader ledger,
            TestClock clock) {
        this.client = client;
        this.context = context;
        this.jdbc = jdbc;
        this.ledger = ledger;
        this.clock = clock;
    }

    @Given("user {int} placed an order:")
    @When("user {int} places an order:")
    public void place(int userId, DataTable order) {
        placeWithKey(userId, UUID.randomUUID().toString(), order);
    }

    @Given("user {int} placed an order with idempotency key {string}:")
    @When("user {int} places an order with idempotency key {string}:")
    public void placeWithKey(int userId, String key, DataTable order) {
        send(new Request(userId, key, body(order.asMap())));
    }

    @When("user {int} places an order without idempotency key:")
    public void placeWithoutKey(int userId, DataTable order) {
        placeWithKey(userId, null, order);
    }

    @When("user {int} sends an order with the raw JSON body:")
    public void sendRawJson(int userId, String json) {
        send(new Request(userId, UUID.randomUUID().toString(), json));
    }

    @When("user {int} sends the same order again")
    public void resend(int userId) {
        assertThat(lastRequest.userId()).isEqualTo(userId);
        originalOrder = context.body();
        send(lastRequest);
    }

    @Given("user {int} made a deposit of {string} with idempotency key {string}")
    public void deposit(int userId, String amount, String key) {
        Money money = Money.of(amount);
        jdbc.sql("""
                INSERT INTO orders (instrumentid, userid, size, price, type, side, status, datetime,
                                    idempotency_key, request_fingerprint)
                VALUES (66, :userId, :amount, 1.00, 'MARKET', 'CASH_IN', 'FILLED', CURRENT_TIMESTAMP, :key, :fingerprint)
                """)
                .param("userId", userId).param("amount", money.amount()).param("key", key)
                .param("fingerprint", RequestFingerprint.of("CASH_IN|amount=" + money.amount().toPlainString()).hex())
                .update();
    }

    @Given("the current time is {string}")
    public void setTime(String instant) {
        clock.fix(Instant.parse(instant));
    }

    @When("user {int} places these orders at the same time:")
    public void placeConcurrently(int userId, DataTable orders) throws Exception {
        List<Request> requests = orders.asMaps().stream().map(row -> {
            Map<String, String> fields = new LinkedHashMap<>(row);
            String key = fields.remove("idempotency key");
            return new Request(userId, key, body(fields));
        }).toList();
        CountDownLatch ready = new CountDownLatch(requests.size());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(requests.size())) {
            List<Future<EntityExchangeResult<JsonNode>>> responses = new ArrayList<>();
            for (Request request : requests) {
                responses.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return exchange(request);
                }));
            }
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            for (var response : responses) {
                concurrentResponses.add(response.get(10, TimeUnit.SECONDS));
            }
        }
    }

    @Then("the order is created with status {string}")
    public void orderCreated(String status) {
        assertThat(context.status()).isEqualTo(201);
        orderStatus(status);
        long orderId = context.body().required("orderId").longValue();
        assertThat(jdbc.sql("SELECT status FROM orders WHERE id = :id").param("id", orderId)
                .query(String.class).single()).isEqualTo(status);
    }

    @Then("the order status is {string}")
    public void orderStatus(String status) {
        assertField("status", status);
    }

    @Then("the order quantity is {int}")
    public void orderQuantity(int quantity) {
        JsonNode value = context.body().required("quantity");
        assertThat(value.isIntegralNumber()).isTrue();
        assertThat(value.longValue()).isEqualTo(quantity);
    }

    @Then("the order price is {string}")
    public void orderPrice(String price) {
        assertField("price", price);
    }

    @Then("the order has no price")
    public void noPrice() {
        assertThat(context.body().required("price").isNull()).isTrue();
    }

    @Then("the order total amount is {string}")
    public void totalAmount(String total) {
        assertField("totalAmount", total);
    }

    @Then("the order requested amount is {string} and the remaining amount is {string}")
    public void amounts(String requested, String remaining) {
        assertField("requestedAmount", requested);
        assertField("remainingAmount", remaining);
    }

    @Then("the order rejection is {string} with message {string}")
    public void orderRejection(String code, String message) {
        JsonNode rejection = context.body().required("rejection");
        assertThat(rejection.required("code").asText()).isEqualTo(code);
        assertThat(rejection.required("message").asText()).isEqualTo(message);
    }

    @Then("the available cash of user {int} is {string}")
    public void availableCash(int userId, String expected) {
        assertThat(ledger.cashBalance(new UserId(userId)).available().amount().toPlainString()).isEqualTo(expected);
    }

    @Then("user {int} holds {int} shares of {string}")
    public void sharesHeld(int userId, int quantity, String ticker) {
        assertThat(ledger.shareBalance(new UserId(userId), new InstrumentId(instrumentId(ticker))).quantity())
                .isEqualTo(quantity);
    }

    @Then("user {int} can sell {int} shares of {string}")
    public void sharesAvailable(int userId, int quantity, String ticker) {
        assertThat(ledger.shareBalance(new UserId(userId), new InstrumentId(instrumentId(ticker))).available())
                .isEqualTo(quantity);
    }

    @Then("no order is stored")
    public void noOrderStored() {
        assertThat(orderCount()).isEqualTo(ordersBeforeRequest);
    }

    @Then("the response is a replay of the original order")
    public void replay() {
        assertThat(context.status()).isEqualTo(200);
        assertThat(context.headers().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(context.body().required("orderId")).isEqualTo(originalOrder.required("orderId"));
        assertThat(context.body()).isEqualTo(originalOrder);
    }

    @Then("one order is {string} and the other is {string} with reason {string}")
    public void concurrentOutcomes(String first, String second, String reason) {
        assertThat(concurrentResponses).allSatisfy(response -> assertThat(response.getStatus().value()).isEqualTo(201));
        assertThat(concurrentResponses).map(response -> response.getResponseBody().required("status").asText())
                .containsExactlyInAnyOrder(first, second);
        assertThat(concurrentResponses).filteredOn(response -> second.equals(
                        response.getResponseBody().required("status").asText())).singleElement().satisfies(response ->
                        assertThat(response.getResponseBody().required("rejection").required("code").asText())
                                .isEqualTo(reason));
    }

    @Then("the order timestamp is {string}")
    public void timestamp(String timestamp) {
        assertField("createdAt", timestamp);
    }

    private void assertField(String field, String expected) {
        JsonNode value = context.body().required(field);
        assertThat(value.isString()).as(field + " is a JSON string").isTrue();
        assertThat(value.asText()).as(field).isEqualTo(expected);
    }

    private Map<String, Object> body(Map<String, String> fields) {
        Map<String, Object> body = new LinkedHashMap<>();
        fields.forEach((field, value) -> {
            switch (field) {
                case "instrument" -> body.put("instrumentId", instrumentId(value));
                case "amount", "price", "side", "type" -> body.put(field, value);
                default -> body.put(field, Long.parseLong(value));
            }
        });
        return body;
    }

    private long instrumentId(String ticker) {
        return jdbc.sql("SELECT id FROM instruments WHERE ticker = :ticker").param("ticker", ticker)
                .query(Long.class).single();
    }

    private long orderCount() {
        return jdbc.sql("SELECT COUNT(*) FROM orders").query(Long.class).single();
    }

    private void send(Request request) {
        lastRequest = request;
        ordersBeforeRequest = orderCount();
        context.record(exchange(request));
    }

    private EntityExchangeResult<JsonNode> exchange(Request order) {
        var request = client.post().uri("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", Integer.toString(order.userId()));
        if (order.key() != null) {
            request.header("Idempotency-Key", order.key());
        }
        return request.body(order.body()).exchange().returnResult(JsonNode.class);
    }

    private record Request(int userId, String key, Object body) {
    }
}
