package com.cocos.challenge.infrastructure.adapter.in.rest.internal;

import java.time.LocalDate;

public record MarketDataView(
        long instrumentId, String ticker, LocalDate date, String close, String previousClose) {
}
