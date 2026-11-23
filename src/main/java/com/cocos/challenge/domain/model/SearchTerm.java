package com.cocos.challenge.domain.model;

import com.cocos.challenge.domain.exception.InvalidRequestException;

public record SearchTerm(String text) {

    public SearchTerm {
        if (text == null || text.isBlank() || !text.equals(text.trim())) {
            throw new IllegalArgumentException("Search term must be trimmed and non-blank.");
        }
    }

    public static SearchTerm of(String parameterName, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.codePointCount(0, trimmed.length()) > 100) {
            throw new InvalidRequestException(
                    "Request validation failed: '" + parameterName + "' must have at most 100 characters.");
        }
        return new SearchTerm(trimmed);
    }

    public boolean isShort() {
        return text.codePointCount(0, text.length()) < 3;
    }
}
