package com.cocos.challenge.bdd.steps;

import com.cocos.challenge.bdd.support.ScenarioContext;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

public class InternalToolsSteps {

    private final RestTestClient client;
    private final ScenarioContext context;
    private final JdbcClient jdbc;
    private final PortfolioSteps portfolioSteps;

    public InternalToolsSteps(RestTestClient client, ScenarioContext context, JdbcClient jdbc,
            PortfolioSteps portfolioSteps) {
        this.client = client;
        this.context = context;
        this.jdbc = jdbc;
        this.portfolioSteps = portfolioSteps;
    }

    @When("a user without the administrator role calls {string} {string}")
    public void callWithoutAdminRole(String method, String path) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Then("the seed data is unchanged")
    public void seedDataIsUnchanged() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @When("the administrator forces the execution of order {long}")
    public void forceExecution(long orderId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @When("the administrator forces the execution of that order")
    public void forceExecutionOfThatOrder() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Then("the order record has status {string} and price {string}")
    public void orderRecordStatusAndPrice(String status, String price) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @When("the administrator deletes order {long}")
    public void deleteOrder(long orderId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Given("the administrator deleted that order")
    public void deletedThatOrder() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Then("order {long} does not exist")
    public void orderDoesNotExist(long orderId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Then("the order id is different from the deleted order")
    public void orderIdDifferentFromDeleted() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Given("the administrator set the market data of {string} to close {string}")
    public void setMarketDataGiven(String ticker, String close) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @When("the administrator sets the market data of {string} to close {string}")
    public void setMarketData(String ticker, String close) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @When("the administrator restores the seed data")
    public void restoreSeedData() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @When("the administrator creates a user with email {string}")
    public void createUser(String email) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Then("the created user has id {int}, email {string} and account number {string}")
    public void createdUserMatches(int userId, String email, String accountNumber) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Then("the portfolio of user {int} has cash {string} with {string} reserved and {string} available")
    public void portfolioCash(int userId, String total, String reserved, String available) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Then("the portfolio of user {int} has total value {string}")
    public void portfolioTotalValue(int userId, String expected) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Then("the portfolio of user {int} has the warning {string} with message {string}")
    public void portfolioWarning(int userId, String code, String message) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Then("the portfolio of user {int} has the position:")
    public void portfolioPosition(int userId, DataTable expected) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Then("the portfolio of user {int} has a position {string} with quantity {int} and no last price, market value, total return or daily change")
    public void portfolioUnpricedPosition(int userId, String ticker, int quantity) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
