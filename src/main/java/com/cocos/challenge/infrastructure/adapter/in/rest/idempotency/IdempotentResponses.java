package com.cocos.challenge.infrastructure.adapter.in.rest.idempotency;

import org.springframework.http.ResponseEntity;

/** Response helpers shared by operation endpoints (specs 005 and 007). */
public final class IdempotentResponses {

    private IdempotentResponses() {
    }

    public static <T> ResponseEntity<T> created(T body) {
        throw new UnsupportedOperationException();
    }

    public static <T> ResponseEntity<T> replayed(T body) {
        throw new UnsupportedOperationException();
    }
}
