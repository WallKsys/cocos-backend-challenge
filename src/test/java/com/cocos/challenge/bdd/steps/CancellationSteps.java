package com.cocos.challenge.bdd.steps;

import com.cocos.challenge.application.port.out.AccountLock;
import com.cocos.challenge.bdd.support.ScenarioContext;
import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.support.ForcedExecutionSimulator;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class CancellationSteps {

    private final RestTestClient client;
    private final ScenarioContext context;
    private final JdbcClient jdbc;
    private final PlatformTransactionManager transactionManager;
    private final AccountLock accountLock;
    private int forcedExecutionUpdates;

    public CancellationSteps(RestTestClient client, ScenarioContext context, JdbcClient jdbc,
            PlatformTransactionManager transactionManager, AccountLock accountLock) {
        this.client = client;
        this.context = context;
        this.jdbc = jdbc;
        this.transactionManager = transactionManager;
        this.accountLock = accountLock;
    }

    @Given("user {int} cancelled order {long}")
    public void cancelled(int userId, long orderId) {
        cancel(userId, orderId);
        assertThat(context.status()).isEqualTo(200);
    }

    @When("user {int} cancels order {long}")
    public void cancel(int userId, long orderId) {
        context.record(exchangeCancel(userId, orderId));
    }

    @When("user {int} cancels that order")
    public void cancelLastOrder(int userId) {
        assertThat(context.status()).isEqualTo(201);
        cancel(userId, context.body().required("orderId").asLong());
    }

    @When("user {int} cancels order {long} while the administrator forces its execution at the same time")
    public void cancelWhileForcedExecution(int userId, long orderId) throws Exception {
        var simulator = new ForcedExecutionSimulator(new TransactionTemplate(transactionManager), accountLock, jdbc);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var cancelFuture = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return exchangeCancel(userId, orderId);
            });
            var executionFuture = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return simulator.execute(new UserId(userId), orderId);
            });
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            context.record(cancelFuture.get(10, TimeUnit.SECONDS));
            forcedExecutionUpdates = executionFuture.get(10, TimeUnit.SECONDS);
        }
    }

    @Then("the cancelled order has status {string} and was not already cancelled")
    public void cancelledFresh(String status) {
        assertCancelled(status, false);
    }

    @Then("the cancelled order has status {string} and was already cancelled")
    public void cancelledAgain(String status) {
        assertCancelled(status, true);
    }

    @Then("order {long} has status {string}")
    public void orderHasStatus(long orderId, String status) {
        assertThat(storedStatus(orderId)).isEqualTo(status);
    }

    @Then("exactly one of the two operations is applied to order {long}")
    public void exactlyOneApplied(long orderId) {
        int cancelUpdates = context.status() == 200 && !context.body().required("alreadyCancelled").asBoolean() ? 1 : 0;
        assertThat(forcedExecutionUpdates).isBetween(0, 1);
        assertThat(cancelUpdates + forcedExecutionUpdates).isEqualTo(1);
        assertThat(storedStatus(orderId)).isIn("CANCELLED", "FILLED");
    }

    @Then("the cancellation response is consistent with the final status of order {long}")
    public void consistentWithFinalStatus(long orderId) {
        String finalStatus = storedStatus(orderId);
        switch (finalStatus) {
            case "CANCELLED" -> {
                assertThat(forcedExecutionUpdates).isZero();
                assertThat(context.status()).isEqualTo(200);
                assertCancelled("CANCELLED", false);
            }
            case "FILLED" -> {
                assertThat(forcedExecutionUpdates).isEqualTo(1);
                assertThat(context.status()).isEqualTo(409);
                assertThat(context.body().required("code").asString()).isEqualTo("ORDER_NOT_CANCELLABLE");
            }
            default -> throw new AssertionError("Unexpected final status for order " + orderId + ": " + finalStatus);
        }
    }

    private void assertCancelled(String status, boolean alreadyCancelled) {
        assertThat(context.body().required("status").asString()).isEqualTo(status);
        JsonNode flag = context.body().required("alreadyCancelled");
        assertThat(flag.isBoolean()).isTrue();
        assertThat(flag.asBoolean()).isEqualTo(alreadyCancelled);
    }

    private EntityExchangeResult<JsonNode> exchangeCancel(int userId, long orderId) {
        return client.post().uri("/api/v1/orders/{orderId}/cancel", orderId)
                .header("X-User-Id", Integer.toString(userId))
                .exchange().returnResult(JsonNode.class);
    }

    private String storedStatus(long orderId) {
        return jdbc.sql("SELECT status FROM orders WHERE id = :id").param("id", orderId)
                .query(String.class).single();
    }
}
