package com.cocos.challenge.infrastructure.adapter.in.rest.instrument;

import com.cocos.challenge.support.IntegrationTest;
import com.cocos.challenge.support.SeedData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Tag("integration")
class InstrumentSearchControllerIT {

    @Autowired
    RestTestClient restTestClient;

    @Autowired
    JdbcClient jdbcClient;

    @BeforeEach
    void resetDatabase() {
        SeedData.reset(jdbcClient);
    }

    @Test
    @Tag("CU-02.11")
    void returnsInstrumentAndMarketDataWithDecimalStrings() {
        get("/api/v1/instruments?q=pamp").expectStatus().isOk().expectBody()
                .jsonPath("$.items.length()").isEqualTo(1)
                .jsonPath("$.items[0].id").isEqualTo(47)
                .jsonPath("$.items[0].ticker").isEqualTo("PAMP")
                .jsonPath("$.items[0].name").isEqualTo("Pampa Holding S.A.")
                .jsonPath("$.items[0].type").isEqualTo("ACCIONES")
                .jsonPath("$.items[0].lastPrice").isEqualTo("925.85")
                .jsonPath("$.items[0].dailyChangePct").isEqualTo("0.44")
                .jsonPath("$.nextCursor").value(Object.class, value -> assertThat(value).isNull());
    }

    @ParameterizedTest
    @ValueSource(strings = {"b", "ba"})
    @Tag("CU-02.13")
    void rejectsShortNameFilter(String name) {
        getWithParameter("name", name).expectStatus().isBadRequest().expectBody()
                .jsonPath("$.code").isEqualTo("SEARCH_TERM_TOO_SHORT")
                .jsonPath("$.detail").isEqualTo("The name filter requires at least 3 characters.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"50%", "50_", "50\\"})
    void treatsSearchPunctuationAsLiteralCharacters(String query) {
        getWithParameter("q", query).expectStatus().isOk().expectBody()
                .jsonPath("$.items.length()").isEqualTo(0);
    }

    @Test
    void treatsBlankParametersAsAbsent() {
        for (String query : new String[]{"q=", "q=%20%20", "ticker=%20", "name=", "cursor=%20%20"}) {
            get("/api/v1/instruments?" + query).expectStatus().isOk().expectBody()
                    .jsonPath("$.items.length()").isEqualTo(20)
                    .jsonPath("$.items[0].ticker").isEqualTo("AGRO");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"q", "ticker", "name"})
    void rejectsParameterLongerThanOneHundredCharacters(String parameter) {
        assertValidationError("/api/v1/instruments?" + parameter + "=" + "x".repeat(101),
                "Request validation failed: '" + parameter + "' must have at most 100 characters.");
    }

    @Test
    void rejectsLimitOutsideItsAllowedRange() {
        for (String limit : new String[]{"0", "101"}) {
            assertValidationError("/api/v1/instruments?limit=" + limit,
                    "Request validation failed: 'limit' must be between 1 and 100.");
        }
    }

    @Test
    void rejectsNonIntegerLimit() {
        assertValidationError("/api/v1/instruments?limit=abc",
                "Request validation failed: 'limit' has an invalid type or format.");
    }

    @Test
    void rejectsInvalidCursor() {
        assertValidationError("/api/v1/instruments?cursor=not-base64",
                "Request validation failed: 'cursor' is invalid.");
    }

    private RestTestClient.ResponseSpec get(String uri) {
        return restTestClient.get().uri(uri).exchange();
    }

    private RestTestClient.ResponseSpec getWithParameter(String name, String value) {
        return restTestClient.get().uri(builder -> builder.path("/api/v1/instruments")
                .queryParam(name, value).build()).exchange();
    }

    private void assertValidationError(String uri, String detail) {
        get(uri).expectStatus().isBadRequest().expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.detail").isEqualTo(detail);
    }
}
