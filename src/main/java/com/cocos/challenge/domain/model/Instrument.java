package com.cocos.challenge.domain.model;

import java.util.Objects;

/**
 * Instrument an order can reference (spec 005, plan.md § Order evaluation).
 */
public record Instrument(InstrumentId id, String ticker, InstrumentType type) {

    public Instrument {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(type, "type");
    }
}
