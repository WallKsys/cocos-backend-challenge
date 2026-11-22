package com.cocos.challenge.infrastructure.adapter.in.rest.error;

public class ApiException extends RuntimeException {

    public ApiException(ApiErrorCode code, String detail) {
        super(detail);
    }

    public ApiErrorCode code() {
        throw new UnsupportedOperationException();
    }
}
