package com.cocos.challenge.bdd.support;

import io.cucumber.spring.ScenarioScope;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import tools.jackson.databind.JsonNode;

@Component
@ScenarioScope
public class ScenarioContext {

    private int status;
    private HttpHeaders headers;
    private JsonNode body;

    public void record(EntityExchangeResult<JsonNode> response) {
        status = response.getStatus().value();
        headers = response.getResponseHeaders();
        body = response.getResponseBody();
    }

    public int status() {
        return status;
    }

    public HttpHeaders headers() {
        return headers;
    }

    public JsonNode body() {
        return body;
    }
}
