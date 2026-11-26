package com.cocos.challenge.domain.exception;

public non-sealed class SearchTermTooShortException extends DomainException {

    public SearchTermTooShortException() {
        super("The name filter requires at least 3 characters.");
    }
}
