package com.cocos.challenge.domain.model;

import com.cocos.challenge.domain.exception.InvalidRequestException;

public record UserId(long value) {

    public UserId {
        if (value < 1 || value > Integer.MAX_VALUE) {
            throw new InvalidRequestException("User id must be between 1 and 2147483647.");
        }
    }
}
