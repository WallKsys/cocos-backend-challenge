package com.cocos.challenge.bdd.support;

import io.cucumber.spring.ScenarioScope;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import tools.jackson.databind.JsonNode;

@Component
@ScenarioScope
public class ScenarioContext {

    public void record(EntityExchangeResult<JsonNode> response) {
    }

    public int status() {
        return 0;
    }

    public HttpHeaders headers() {
        return null;
    }

    public JsonNode body() {
        return null;
    }
}
