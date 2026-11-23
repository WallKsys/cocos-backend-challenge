package com.cocos.challenge.domain.model;

import com.cocos.challenge.domain.exception.InvalidRequestException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public record SearchCursor(int rank, String ticker) {

    public SearchCursor {
        if (rank < 0 || rank > 2 || ticker == null || ticker.isEmpty()) {
            throw new IllegalArgumentException("Invalid search cursor.");
        }
    }

    public String encode() {
        String value = "v1:" + rank + ":" + ticker;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public static SearchCursor decode(String encoded) {
        if (encoded == null) {
            throw invalidCursor();
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":", 3);
            if (parts.length != 3 || !parts[0].equals("v1")) {
                throw new IllegalArgumentException();
            }
            return new SearchCursor(Integer.parseInt(parts[1]), parts[2]);
        } catch (IllegalArgumentException exception) {
            throw invalidCursor();
        }
    }

    private static InvalidRequestException invalidCursor() {
        return new InvalidRequestException("Request validation failed: 'cursor' is invalid.");
    }
}
