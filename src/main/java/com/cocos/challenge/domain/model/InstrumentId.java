package com.cocos.challenge.domain.model;

public record InstrumentId(long value) {

    public InstrumentId {
        if (value < 1) {
            throw new IllegalArgumentException("Instrument id must be at least 1.");
        }
    }
}
