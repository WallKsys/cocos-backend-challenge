package com.cocos.challenge.infrastructure.adapter.in.rest.order;

import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.domain.model.Order;
import com.cocos.challenge.infrastructure.adapter.in.rest.format.ApiDecimals;

import java.time.Instant;

public record OrderResponse(
        long orderId, long instrumentId, String ticker, String side, String type, String status,
        long quantity, String price, String totalAmount, String requestedAmount, String remainingAmount,
        Instant createdAt, RejectionResponse rejection) {

    static OrderResponse from(Order order) {
        var rejection = order.rejection();
        return new OrderResponse(order.id(), order.instrumentId().value(), order.ticker(), order.side().name(),
                order.type().name(), order.status().name(), order.quantity(), format(order.price()),
                format(order.totalAmount()), format(order.requestedAmount()), format(order.remainingAmount()),
                order.createdAt(), rejection == null ? null : new RejectionResponse(
                        rejection.reason().name(), rejection.message()));
    }

    private static String format(Money value) {
        return value == null ? null : ApiDecimals.format(value);
    }

    public record RejectionResponse(String code, String message) {
    }
}
