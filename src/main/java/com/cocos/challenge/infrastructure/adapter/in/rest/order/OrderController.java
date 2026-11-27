package com.cocos.challenge.infrastructure.adapter.in.rest.order;

import com.cocos.challenge.application.port.in.CancelOrderUseCase;
import com.cocos.challenge.application.port.in.OrderQuery;
import com.cocos.challenge.application.port.in.PlaceOrderUseCase;
import com.cocos.challenge.application.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import com.cocos.challenge.domain.model.OrderHistoryCriteria;
import com.cocos.challenge.domain.model.OrderRequest;
import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.infrastructure.adapter.in.rest.format.DecimalString;
import com.cocos.challenge.infrastructure.adapter.in.rest.idempotency.IdempotencyKeyHeader;
import com.cocos.challenge.infrastructure.adapter.in.rest.idempotency.IdempotentResponses;
import com.cocos.challenge.infrastructure.adapter.in.rest.identity.RequesterId;
import com.cocos.challenge.infrastructure.adapter.in.rest.tracing.OperationLog;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final PlaceOrderUseCase placeOrder;
    private final OrderQuery orders;
    private final CancelOrderUseCase cancellation;

    public OrderController(PlaceOrderUseCase placeOrder, OrderQuery orders, CancelOrderUseCase cancellation) {
        this.placeOrder = placeOrder;
        this.orders = orders;
        this.cancellation = cancellation;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> place(@RequesterId UserId userId, @IdempotencyKeyHeader String key,
                                               @Valid @RequestBody PlaceOrderRequestBody body) {
        var request = OrderRequest.of(body.instrumentId(), body.side(), body.type(), body.quantity(),
                body.amount(), body.price());
        var result = placeOrder.place(new PlaceOrderCommand(userId, key, request));
        OperationLog.orderPlaced(userId, key, result);
        var response = OrderResponse.from(result.order());
        return result.replayed() ? IdempotentResponses.replayed(response) : IdempotentResponses.created(response);
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

    public record PlaceOrderRequestBody(
            @NotNull @Positive Long instrumentId,
            @NotNull String side,
            @NotNull String type,
            Integer quantity,
            @DecimalString String amount,
            @DecimalString String price) {
    }
}
