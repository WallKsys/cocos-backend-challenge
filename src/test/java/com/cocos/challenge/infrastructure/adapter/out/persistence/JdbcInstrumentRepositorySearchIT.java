package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.InstrumentRepository;
import com.cocos.challenge.application.port.out.InstrumentRepository.RankedInstrument;
import com.cocos.challenge.domain.model.InstrumentSearchCriteria;
import com.cocos.challenge.domain.model.SearchCursor;
import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Tag("integration")
class JdbcInstrumentRepositorySearchIT {

    @Autowired
    InstrumentRepository repository;

    @Autowired
    JdbcClient jdbcClient;

    @Test
    @Tag("CU-02.01")
    void partialNameMatch() {
        assertTickers(q("gal"), "GGAL");
    }

    @Test
    @Tag("CU-02.02")
    void matchingIgnoresAccents() {
        assertTickers(q("rio"), "BHIP", "MOLI", "OEST", "RICH");
    }

    @Test
    @Tag("CU-02.03")
    void matchingIgnoresCase() {
        for (String query : List.of("BANCO", "banco")) {
            assertTickers(q(query), "BBAR", "BHIP", "BMA", "BPAT");
        }
    }

    @Test
    @Tag("CU-02.04")
    void exactTickerComesFirst() {
        assertTickers(q("pamp"), "PAMP");
    }

    @Test
    @Tag("CU-02.05")
    void resultsAreRankedByExactTickerPrefixThenOtherMatches() {
        assertTickers(q("ban"), "BBAR", "BHIP", "BMA", "BPAT", "GBAN");
    }

    @Test
    @Tag("CU-02.05b")
    void shortGeneralTermsOnlyMatchExactTickers() {
        for (String query : List.of("b", "ba")) {
            assertTickers(q(query));
        }
    }

    @Test
    @Tag("CU-02.05c")
    @Transactional
    void shortExactTickerDoesNotReturnPartialMatches() {
        insert("TS", "Tenaris S.A.");
        insert("TSLA", "Tesla Inc. CEDEAR");

        assertTickers(q("ts"), "TS");
    }

    @Test
    @Tag("CU-02.06")
    void tickerAndNameFiltersMustBothMatch() {
        assertTickers(criteria(null, "bma", "banco", 100, null), "BMA");
    }

    @Test
    @Tag("CU-02.07")
    void noCriteriaReturnsTheFirstTwentyInstrumentsAlphabetically() {
        List<RankedInstrument> results = repository.search(criteria(null, null, null, 20, null), 21);

        assertThat(results).hasSize(21);
        assertThat(tickers(results)).startsWith("AGRO", "ALUA", "AUSO", "BBAR", "BHIP", "BMA");
    }

    @Test
    @Tag("CU-02.08")
    void cashIsNeverReturned() {
        for (String query : List.of("ars", "pesos")) {
            assertTickers(q(query));
        }
    }

    @Test
    @Tag("CU-02.09")
    void noMatchesReturnsAnEmptyList() {
        assertTickers(q("xyz123"));
    }

    @Test
    @Tag("CU-02.10")
    void instrumentWithoutMarketDataHasNoQuote() {
        List<RankedInstrument> results = repository.search(q("PGR"), 100);

        assertThat(tickers(results)).containsExactly("PGR");
        assertThat(results.getFirst().instrument().quote()).isNull();
    }

    @Test
    @Tag("CU-02.12")
    void paginationReturnsEveryTradableInstrumentOnceInOrder() {
        List<Integer> pageSizes = new ArrayList<>();
        List<String> allTickers = new ArrayList<>();
        String cursor = null;
        do {
            List<RankedInstrument> results = repository.search(criteria(null, null, null, 20, cursor), 21);
            List<RankedInstrument> page = results.stream().limit(20).toList();
            pageSizes.add(page.size());
            allTickers.addAll(tickers(page));
            cursor = results.size() > 20 ? cursorOf(page.getLast()) : null;
        } while (cursor != null);

        assertThat(pageSizes).containsExactly(20, 20, 20, 5);
        assertThat(allTickers).hasSize(65).isSorted().doesNotHaveDuplicates();
    }

    @Test
    @Tag("CU-02.14")
    void generalTermCombinesWithTickerFilter() {
        assertTickers(criteria("banco", "bma", null, 100, null), "BMA");
    }

    @Test
    @Tag("CU-02.15")
    void shortTickerFilterOnlyMatchesExactTicker() {
        assertTickers(criteria("banco", "bm", null, 100, null));
    }

    private void assertTickers(InstrumentSearchCriteria criteria, String... expected) {
        assertThat(tickers(repository.search(criteria, 100))).containsExactly(expected);
    }

    private void insert(String ticker, String name) {
        jdbcClient.sql("INSERT INTO instruments (ticker, name, type) VALUES (:ticker, :name, 'ACCIONES')")
                .param("ticker", ticker).param("name", name).update();
    }

    private static InstrumentSearchCriteria q(String q) {
        return criteria(q, null, null, 100, null);
    }

    private static InstrumentSearchCriteria criteria(
            String q, String ticker, String name, Integer limit, String cursor) {
        return InstrumentSearchCriteria.of(q, ticker, name, limit, cursor);
    }

    private static List<String> tickers(List<RankedInstrument> results) {
        return results.stream().map(result -> result.instrument().ticker()).toList();
    }

    private static String cursorOf(RankedInstrument result) {
        return new SearchCursor(result.rank(), result.instrument().ticker()).encode();
    }
}
