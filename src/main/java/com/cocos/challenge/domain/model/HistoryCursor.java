package com.cocos.challenge.domain.model;

import java.time.Instant;

public record HistoryCursor(Instant createdAt, long orderId) {

    public String encode() {
        throw new UnsupportedOperationException();
    }

    public static HistoryCursor decode(String encoded) {
        throw new UnsupportedOperationException();
    }
}
