package com.cocos.challenge.application.port.in;

import com.cocos.challenge.domain.model.Order;
import com.cocos.challenge.domain.model.OrderRequest;
import com.cocos.challenge.domain.model.UserId;

public interface PlaceOrderUseCase {

    PlaceOrderResult place(PlaceOrderCommand command);

    record PlaceOrderCommand(UserId userId, String idempotencyKey, OrderRequest request) {
    }

    record PlaceOrderResult(Order order, boolean replayed) {
    }
}
