package com.cocos.challenge.domain.exception;

public abstract sealed class DomainException extends RuntimeException
        permits IdempotencyConflictException, InvalidRequestException, NotFoundException,
        OrderNotCancellableException, OrderNotExecutableException, SearchTermTooShortException {

    protected DomainException(String message) {
        super(message);
    }
}
