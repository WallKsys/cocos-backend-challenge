package com.cocos.challenge.application.port.in;

import com.cocos.challenge.domain.model.Order;

public record PlaceOrderResult(Order order, boolean replayed) {
}
