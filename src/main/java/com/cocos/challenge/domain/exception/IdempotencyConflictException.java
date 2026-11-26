package com.cocos.challenge.domain.exception;

public non-sealed class IdempotencyConflictException extends DomainException {

    public IdempotencyConflictException(String idempotencyKey) {
        super("Idempotency key '" + idempotencyKey + "' was already used for a different request.");
    }
}
