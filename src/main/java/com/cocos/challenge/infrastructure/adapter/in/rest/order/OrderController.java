package com.cocos.challenge.infrastructure.adapter.in.rest.order;

import com.cocos.challenge.application.port.in.PlaceOrderCommand;
import com.cocos.challenge.application.port.in.PlaceOrderUseCase;
import com.cocos.challenge.domain.model.OrderRequest;
import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.infrastructure.adapter.in.rest.idempotency.IdempotencyKeyHeader;
import com.cocos.challenge.infrastructure.adapter.in.rest.idempotency.IdempotentResponses;
import com.cocos.challenge.infrastructure.adapter.in.rest.identity.RequesterId;
import com.cocos.challenge.infrastructure.adapter.in.rest.tracing.OperationLog;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private final PlaceOrderUseCase placeOrder;

    public OrderController(PlaceOrderUseCase placeOrder) {
        this.placeOrder = placeOrder;
    }

    @PostMapping("/api/v1/orders")
    public ResponseEntity<OrderResponse> place(@RequesterId UserId userId, @IdempotencyKeyHeader String key,
                                               @Valid @RequestBody PlaceOrderRequestBody body) {
        var request = OrderRequest.of(body.instrumentId(), body.side(), body.type(), body.quantity(),
                body.amount(), body.price());
        var result = placeOrder.place(new PlaceOrderCommand(userId, key, request));
        OperationLog.orderPlaced(userId, key, result);
        var response = OrderResponse.from(result.order());
        return result.replayed() ? IdempotentResponses.replayed(response) : IdempotentResponses.created(response);
    }
}
