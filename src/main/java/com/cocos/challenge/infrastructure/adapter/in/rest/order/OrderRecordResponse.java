package com.cocos.challenge.infrastructure.adapter.in.rest.order;

import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.domain.model.OrderRecord;
import com.cocos.challenge.infrastructure.adapter.in.rest.format.ApiDecimals;

import java.time.Instant;

public record OrderRecordResponse(
        long orderId, long instrumentId, String ticker, String side, String type, String status,
        Long quantity, String price, String amount, String requestedAmount, Instant createdAt,
        OrderResponse.RejectionResponse rejection) {

    public static OrderRecordResponse from(OrderRecord order) {
        var rejection = order.rejection();
        return new OrderRecordResponse(order.id(), order.instrumentId().value(), order.ticker(), order.side().name(),
                order.type().name(), order.status().name(), order.quantity(), format(order.price()),
                format(order.amount()), format(order.requestedAmount()), order.createdAt(),
                rejection == null ? null : new OrderResponse.RejectionResponse(
                        rejection.reason().name(), rejection.message()));
    }

    private static String format(Money value) {
        return value == null ? null : ApiDecimals.format(value);
    }
}
