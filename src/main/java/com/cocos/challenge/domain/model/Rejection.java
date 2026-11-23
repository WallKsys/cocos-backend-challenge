package com.cocos.challenge.domain.model;

import java.util.Objects;

/**
 * Why a processed order was rejected, with the exact message returned to the client.
 */
public record Rejection(RejectionReason reason, String message) {

    public Rejection {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(message, "message");
    }
}
