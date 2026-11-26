package com.cocos.challenge.infrastructure.adapter.in.rest.instrument;

import com.cocos.challenge.domain.model.CursorPage;
import com.cocos.challenge.domain.model.InstrumentSummary;
import com.cocos.challenge.domain.model.SearchCursor;

import java.util.List;

public record InstrumentPageResponse(List<InstrumentResponse> items, String nextCursor) {

    static InstrumentPageResponse from(CursorPage<InstrumentSummary, SearchCursor> page) {
        return new InstrumentPageResponse(page.items().stream().map(InstrumentResponse::from).toList(),
                page.nextCursor() == null ? null : page.nextCursor().encode());
    }
}
