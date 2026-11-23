package com.cocos.challenge.infrastructure.adapter.in.rest.instrument;

public record InstrumentResponse(long id, String ticker, String name, String type, String lastPrice,
        String dailyChangePct) {
}
