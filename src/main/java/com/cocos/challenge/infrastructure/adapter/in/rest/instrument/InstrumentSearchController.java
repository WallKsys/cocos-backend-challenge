package com.cocos.challenge.infrastructure.adapter.in.rest.instrument;

import com.cocos.challenge.application.port.in.SearchInstrumentsQuery;

public class InstrumentSearchController {

    private final SearchInstrumentsQuery query;

    public InstrumentSearchController(SearchInstrumentsQuery query) {
        this.query = query;
    }

    public InstrumentPageResponse search(String q, String ticker, String name, Integer limit, String cursor) {
        throw new UnsupportedOperationException();
    }
}
