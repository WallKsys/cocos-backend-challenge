package com.cocos.challenge.infrastructure.adapter.in.rest.instrument;

import com.cocos.challenge.domain.model.InstrumentPage;

import java.util.List;

public record InstrumentPageResponse(List<InstrumentResponse> items, String nextCursor) {

    static InstrumentPageResponse from(InstrumentPage page) {
        return new InstrumentPageResponse(page.items().stream().map(InstrumentResponse::from).toList(),
                page.nextCursor() == null ? null : page.nextCursor().encode());
    }
}
