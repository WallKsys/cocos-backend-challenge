package com.cocos.challenge.domain.exception;

public class InvalidRequestException extends DomainException {

    public InvalidRequestException(String detail) {
        super(detail);
    }
}
