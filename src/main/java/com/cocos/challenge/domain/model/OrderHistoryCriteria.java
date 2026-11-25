package com.cocos.challenge.domain.model;

public record OrderHistoryCriteria(OrderStatus status, int limit, HistoryCursor cursor) {

    public static OrderHistoryCriteria of(String status, Integer limit, String cursor) {
        throw new UnsupportedOperationException();
    }
}
