package com.cocos.challenge.domain.model;

public record RequestFingerprint(String hex) {

    public RequestFingerprint {
        throw new UnsupportedOperationException();
    }

    public static RequestFingerprint of(String canonicalRequest) {
        throw new UnsupportedOperationException();
    }
}
