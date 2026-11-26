package com.cocos.challenge.domain.exception;

public non-sealed class InvalidRequestException extends DomainException {

    public InvalidRequestException(String detail) {
        super(detail);
    }
}
