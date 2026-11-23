package com.cocos.challenge.domain.model;

public record InstrumentSummary(
        InstrumentId id, String ticker, String name, InstrumentType type, MarketQuote quote) {
}
