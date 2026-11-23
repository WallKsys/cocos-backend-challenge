package com.cocos.challenge.domain.model;

import java.util.Objects;

/**
 * Ownership account of a user (spec 005, plan.md § Order evaluation).
 */
public record Account(UserId userId, String accountNumber) {

    public Account {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(accountNumber, "accountNumber");
    }
}
