package com.cocos.challenge.infrastructure.adapter.in.rest.idempotency;

import org.springframework.http.ResponseEntity;

/** Response helpers shared by operation endpoints (specs 005 and 007). */
public final class IdempotentResponses {

    private IdempotentResponses() {
    }

    public static <T> ResponseEntity<T> created(T body) {
        return ResponseEntity.status(201).body(body);
    }

    public static <T> ResponseEntity<T> replayed(T body) {
        return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(body);
    }
}
