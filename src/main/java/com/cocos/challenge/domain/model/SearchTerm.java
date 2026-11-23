package com.cocos.challenge.domain.model;

public record SearchTerm(String text) {

    public static SearchTerm of(String parameterName, String raw) {
        throw new UnsupportedOperationException();
    }

    public boolean isShort() {
        throw new UnsupportedOperationException();
    }
}
