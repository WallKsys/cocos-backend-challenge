package com.cocos.challenge.infrastructure.adapter.in.rest.order;

import com.cocos.challenge.infrastructure.adapter.in.rest.idempotency.IdempotencyKeyHeader;
import com.cocos.challenge.infrastructure.adapter.in.rest.identity.RequesterId;
import com.cocos.challenge.domain.model.UserId;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    @PostMapping("/api/v1/orders")
    public ResponseEntity<OrderResponse> place(@RequesterId UserId userId, @IdempotencyKeyHeader String key,
                                               @Valid @RequestBody PlaceOrderRequestBody body) {
        throw new UnsupportedOperationException("Order placement endpoint is not implemented");
    }
}
