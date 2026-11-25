package com.cocos.challenge.domain.model;

import com.cocos.challenge.domain.exception.InvalidRequestException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

public record HistoryCursor(Instant createdAt, long orderId) {

    public HistoryCursor {
        if (createdAt == null || orderId < 1) {
            throw new IllegalArgumentException("Invalid history cursor.");
        }
    }

    public String encode() {
        long micros = ChronoUnit.MICROS.between(Instant.EPOCH, createdAt);
        String value = "v1:" + micros + ":" + orderId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public static HistoryCursor decode(String encoded) {
        if (encoded == null) {
            throw invalidCursor();
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":", 3);
            if (parts.length != 3 || !parts[0].equals("v1")) {
                throw new IllegalArgumentException();
            }
            Instant createdAt = Instant.EPOCH.plus(Long.parseLong(parts[1]), ChronoUnit.MICROS);
            return new HistoryCursor(createdAt, Long.parseLong(parts[2]));
        } catch (IllegalArgumentException exception) {
            throw invalidCursor();
        }
    }

    private static InvalidRequestException invalidCursor() {
        return new InvalidRequestException("Request validation failed: 'cursor' is invalid.");
    }
}
