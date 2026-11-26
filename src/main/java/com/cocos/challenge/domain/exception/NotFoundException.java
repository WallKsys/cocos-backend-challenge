package com.cocos.challenge.domain.exception;

public non-sealed class NotFoundException extends DomainException {

    public NotFoundException(String detail) {
        super(detail);
    }
}
