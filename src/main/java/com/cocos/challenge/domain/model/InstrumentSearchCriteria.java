package com.cocos.challenge.domain.model;

import com.cocos.challenge.domain.exception.InvalidRequestException;
import com.cocos.challenge.domain.exception.SearchTermTooShortException;

public record InstrumentSearchCriteria(
        SearchTerm q, SearchTerm ticker, SearchTerm name, int limit, SearchCursor cursor) {

    public static InstrumentSearchCriteria of(String q, String ticker, String name, Integer limit, String cursor) {
        SearchTerm qTerm = SearchTerm.of("q", q);
        SearchTerm tickerTerm = SearchTerm.of("ticker", ticker);
        SearchTerm nameTerm = SearchTerm.of("name", name);
        if (nameTerm != null && nameTerm.isShort()) {
            throw new SearchTermTooShortException();
        }
        int effectiveLimit = limit == null ? 20 : limit;
        if (effectiveLimit < 1 || effectiveLimit > 100) {
            throw new InvalidRequestException("Request validation failed: 'limit' must be between 1 and 100.");
        }
        SearchCursor decodedCursor = cursor == null || cursor.isBlank() ? null : SearchCursor.decode(cursor);
        return new InstrumentSearchCriteria(qTerm, tickerTerm, nameTerm, effectiveLimit, decodedCursor);
    }
}
