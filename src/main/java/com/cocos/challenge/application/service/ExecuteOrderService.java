package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.ExecuteOrderUseCase;
import com.cocos.challenge.application.port.out.AccountLock;
import com.cocos.challenge.application.port.out.AdminOrderRepository;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.domain.exception.NotFoundException;
import com.cocos.challenge.domain.exception.OrderNotExecutableException;
import com.cocos.challenge.domain.model.OrderRecord;
import com.cocos.challenge.domain.model.UserId;
import org.springframework.transaction.annotation.Transactional;

public class ExecuteOrderService implements ExecuteOrderUseCase {

    private final AccountLock lock;
    private final AdminOrderRepository adminOrders;
    private final OrderLedgerRepository ledger;

    public ExecuteOrderService(AccountLock lock, AdminOrderRepository adminOrders, OrderLedgerRepository ledger) {
        this.lock = lock;
        this.adminOrders = adminOrders;
        this.ledger = ledger;
    }

    @Override
    @Transactional
    public OrderRecord execute(long orderId) {
        UserId owner = adminOrders.findOwner(orderId)
                .orElseThrow(() -> new NotFoundException("Order " + orderId + " was not found."));
        lock.lock(owner)
                .orElseThrow(() -> new NotFoundException("Order " + orderId + " was not found."));
        if (!adminOrders.executeIfNew(orderId)) {
            OrderRecord current = ledger.findRecordById(orderId)
                    .orElseThrow(() -> new NotFoundException("Order " + orderId + " was not found."));
            throw new OrderNotExecutableException(orderId, current.status());
        }
        return ledger.findRecordById(orderId)
                .orElseThrow(() -> new NotFoundException("Order " + orderId + " was not found."));
    }
}
