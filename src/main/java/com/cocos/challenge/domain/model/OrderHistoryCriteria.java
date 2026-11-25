package com.cocos.challenge.domain.model;

import com.cocos.challenge.domain.exception.InvalidRequestException;

public record OrderHistoryCriteria(OrderStatus status, int limit, HistoryCursor cursor) {

    public static OrderHistoryCriteria of(String status, Integer limit, String cursor) {
        OrderStatus filter = parseStatus(status);
        int effectiveLimit = limit == null ? 20 : limit;
        if (effectiveLimit < 1 || effectiveLimit > 100) {
            throw new InvalidRequestException("Request validation failed: 'limit' must be between 1 and 100.");
        }
        HistoryCursor decodedCursor = cursor == null ? null : HistoryCursor.decode(cursor);
        return new OrderHistoryCriteria(filter, effectiveLimit, decodedCursor);
    }

    private static OrderStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException(
                    "Request validation failed: 'status' must be one of NEW, FILLED, REJECTED, CANCELLED.");
        }
    }
}
