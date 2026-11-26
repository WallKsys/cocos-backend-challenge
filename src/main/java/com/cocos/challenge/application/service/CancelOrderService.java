package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.CancelOrderUseCase;
import com.cocos.challenge.application.port.out.AccountRepository;
import com.cocos.challenge.application.port.out.BusinessMetrics;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.domain.exception.NotFoundException;
import com.cocos.challenge.domain.exception.OrderNotCancellableException;
import com.cocos.challenge.domain.model.OrderRecord;
import com.cocos.challenge.domain.model.OrderStatus;
import com.cocos.challenge.domain.model.UserId;
import org.springframework.transaction.annotation.Transactional;

public class CancelOrderService implements CancelOrderUseCase {

    private final AccountRepository lock;
    private final OrderLedgerRepository ledger;
    private final BusinessMetrics metrics;

    public CancelOrderService(AccountRepository lock, OrderLedgerRepository ledger, BusinessMetrics metrics) {
        this.lock = lock;
        this.ledger = ledger;
        this.metrics = metrics;
    }

    @Override
    @Transactional
    public CancellationResult cancel(UserId userId, long orderId) {
        lock.lock(userId)
                .orElseThrow(() -> new NotFoundException("User " + userId.value() + " was not found."));

        // TEC-16: Keep the user lock while evaluating the latest status after a missed update.
        while (true) {
            OrderRecord order = ledger.findRecord(userId, orderId)
                    .orElseThrow(() -> new NotFoundException("Order " + orderId + " was not found."));
            switch (order.status()) {
                case CANCELLED -> {
                    metrics.orderCancelled(true);
                    return new CancellationResult(order, true);
                }
                case NEW -> {
                    if (ledger.cancelIfNew(userId, orderId)) {
                        metrics.orderCancelled(false);
                        return new CancellationResult(asCancelled(order), false);
                    }
                }
                case FILLED, REJECTED -> throw new OrderNotCancellableException(orderId, order.status());
            }
        }
    }

    private static OrderRecord asCancelled(OrderRecord order) {
        return new OrderRecord(order.id(), order.instrumentId(), order.ticker(), order.side(), order.type(),
                OrderStatus.CANCELLED, order.size(), order.price(), order.requestedAmount(),
                order.rejection(), order.createdAt());
    }
}
