package com.cocos.challenge.domain.model;

public record SearchCursor(int rank, String ticker) {

    public String encode() {
        throw new UnsupportedOperationException();
    }

    public static SearchCursor decode(String encoded) {
        throw new UnsupportedOperationException();
    }
}
