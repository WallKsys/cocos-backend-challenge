package com.cocos.challenge.infrastructure.adapter.in.rest.order;

import com.cocos.challenge.application.port.in.CancelOrderUseCase;
import com.cocos.challenge.application.port.in.OrderQuery;
import com.cocos.challenge.domain.model.OrderHistoryCriteria;
import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.infrastructure.adapter.in.rest.identity.RequesterId;
import com.cocos.challenge.infrastructure.adapter.in.rest.tracing.OperationLog;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderQueryController {

    private final OrderQuery orders;
    private final CancelOrderUseCase cancellation;

    public OrderQueryController(OrderQuery orders, CancelOrderUseCase cancellation) {
        this.orders = orders;
        this.cancellation = cancellation;
    }

    @GetMapping
    public OrderHistoryPageResponse list(@RequesterId UserId userId,
            @RequestParam(required = false) String status, @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        return OrderHistoryPageResponse.from(orders.list(userId, OrderHistoryCriteria.of(status, limit, cursor)));
    }

    @GetMapping("/{orderId}")
    public OrderRecordResponse get(@RequesterId UserId userId, @PathVariable @Positive long orderId) {
        return OrderRecordResponse.from(orders.get(userId, orderId));
    }

    @PostMapping("/{orderId}/cancel")
    public CancellationResponse cancel(@RequesterId UserId userId, @PathVariable @Positive long orderId) {
        var result = cancellation.cancel(userId, orderId);
        OperationLog.orderCancelled(userId, result);
        return CancellationResponse.from(result);
    }
}
