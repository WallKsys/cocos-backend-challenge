package com.cocos.challenge.bdd.steps;

import com.cocos.challenge.bdd.support.ScenarioContext;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class OrderHistorySteps {

    private final RestTestClient client;
    private final ScenarioContext context;
    private final List<List<Long>> pagedOrderIds = new ArrayList<>();

    public OrderHistorySteps(RestTestClient client, ScenarioContext context) {
        this.client = client;
        this.context = context;
    }

    @When("user {int} requests their order history")
    public void requestHistory(int userId) {
        get(userId, "/api/v1/orders");
    }

    @When("user {int} requests their order history with status {string}")
    public void requestHistoryWithStatus(int userId, String status) {
        get(userId, "/api/v1/orders?status=" + status);
    }

    @When("user {int} requests order {long}")
    public void requestOrder(int userId, long orderId) {
        get(userId, "/api/v1/orders/" + orderId);
    }

    @When("user {int} requests that order")
    public void requestLastOrder(int userId) {
        assertThat(context.status()).isEqualTo(201);
        requestOrder(userId, context.body().required("orderId").asLong());
    }

    @When("the administrator requests the order history of user {int}")
    public void requestAsAdmin(int userId) {
        context.record(client.get().uri("/api/internal/users/{userId}/orders", userId)
                .header("X-Role", "ADMIN")
                .exchange().returnResult(JsonNode.class));
    }

    @When("user {int} pages through their order history with a page size of {int}")
    public void pageThroughHistory(int userId, int limit) {
        pagedOrderIds.clear();
        String cursor = null;
        do {
            String uri = cursor == null
                    ? "/api/v1/orders?limit=" + limit
                    : "/api/v1/orders?limit=" + limit + "&cursor=" + cursor;
            get(userId, uri);
            JsonNode page = okBody();
            pagedOrderIds.add(orderIds(page));
            JsonNode nextCursor = page.required("nextCursor");
            cursor = nextCursor.isNull() ? null : nextCursor.asString();
        } while (cursor != null && pagedOrderIds.size() < 20);
        assertThat(cursor).isNull();
    }

    @Then("the history order ids are {string}")
    public void historyOrderIds(String expected) {
        assertThat(orderIds(okBody())).containsExactlyElementsOf(parseIds(expected));
    }

    @Then("the history entry of order {long} has ticker {string}, side {string}, status {string} and timestamp {string}")
    public void historyEntry(long orderId, String ticker, String side, String status, String timestamp) {
        JsonNode entry = findOrder(okBody(), orderId);
        assertThat(entry.required("ticker").asString()).isEqualTo(ticker);
        assertThat(entry.required("side").asString()).isEqualTo(side);
        assertThat(entry.required("status").asString()).isEqualTo(status);
        assertThat(entry.required("createdAt").asString()).isEqualTo(timestamp);
    }

    @Then("there is no next history page")
    public void noNextPage() {
        assertThat(okBody().required("nextCursor").isNull()).isTrue();
    }

    @Then("the history is empty")
    public void historyIsEmpty() {
        assertThat(okBody().required("items")).isEmpty();
    }

    @Then("the history pages contain the order ids {string}, {string} and {string}")
    public void historyPages(String first, String second, String third) {
        assertThat(pagedOrderIds).containsExactly(parseIds(first), parseIds(second), parseIds(third));
    }

    @Then("the order record has status {string} and rejection {string} with message {string}")
    public void orderRecordWithRejection(String status, String code, String message) {
        JsonNode order = okBody();
        assertThat(order.required("status").asString()).isEqualTo(status);
        JsonNode rejection = order.required("rejection");
        assertThat(rejection.required("code").asString()).isEqualTo(code);
        assertThat(rejection.required("message").asString()).isEqualTo(message);
    }

    @Then("the order record has status {string} and no rejection")
    public void orderRecordWithoutRejection(String status) {
        JsonNode order = okBody();
        assertThat(order.required("status").asString()).isEqualTo(status);
        assertThat(order.required("rejection").isNull()).isTrue();
    }

    private void get(int userId, String uri) {
        context.record(client.get().uri(uri).header("X-User-Id", Integer.toString(userId))
                .exchange().returnResult(JsonNode.class));
    }

    private JsonNode okBody() {
        assertThat(context.status()).isEqualTo(200);
        return context.body();
    }

    private static List<Long> orderIds(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : page.required("items")) {
            ids.add(item.required("orderId").asLong());
        }
        return ids;
    }

    private static JsonNode findOrder(JsonNode page, long orderId) {
        for (JsonNode item : page.required("items")) {
            if (item.required("orderId").asLong() == orderId) {
                return item;
            }
        }
        throw new AssertionError("Order not found in history page: " + orderId);
    }

    private static List<Long> parseIds(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .map(Long::parseLong)
                .toList();
    }
}
