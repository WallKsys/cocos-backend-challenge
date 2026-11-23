package com.cocos.challenge.domain.model;

public record InstrumentSearchCriteria(
        SearchTerm q, SearchTerm ticker, SearchTerm name, int limit, SearchCursor cursor) {

    public static InstrumentSearchCriteria of(String q, String ticker, String name, Integer limit, String cursor) {
        throw new UnsupportedOperationException();
    }
}
