package com.cocos.challenge.infrastructure.adapter.in.rest.instrument;

import java.util.List;

public record InstrumentPageResponse(List<InstrumentResponse> items, String nextCursor) {
}
