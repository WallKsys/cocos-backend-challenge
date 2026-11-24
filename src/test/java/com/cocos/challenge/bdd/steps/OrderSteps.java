package com.cocos.challenge.bdd.steps;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.When;

public class OrderSteps {

    @When("user {int} places an order:")
    public void place(int userId, DataTable order) {
        throw new UnsupportedOperationException("Order steps are not implemented yet");
    }
}
