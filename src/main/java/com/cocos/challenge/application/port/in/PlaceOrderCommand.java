package com.cocos.challenge.application.port.in;

import com.cocos.challenge.domain.model.OrderRequest;
import com.cocos.challenge.domain.model.UserId;

public record PlaceOrderCommand(UserId userId, String idempotencyKey, OrderRequest request) {
}
