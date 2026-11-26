package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.DeleteOrderUseCase;
import com.cocos.challenge.application.port.out.AccountRepository;
import com.cocos.challenge.application.port.out.AdminOrderRepository;
import com.cocos.challenge.domain.exception.NotFoundException;
import com.cocos.challenge.domain.model.UserId;
import org.springframework.transaction.annotation.Transactional;

public class DeleteOrderService implements DeleteOrderUseCase {

    private final AccountRepository lock;
    private final AdminOrderRepository adminOrders;

    public DeleteOrderService(AccountRepository lock, AdminOrderRepository adminOrders) {
        this.lock = lock;
        this.adminOrders = adminOrders;
    }

    @Override
    @Transactional
    public void delete(long orderId) {
        UserId owner = adminOrders.findOwner(orderId)
                .orElseThrow(() -> new NotFoundException("Order " + orderId + " was not found."));
        lock.lock(owner)
                .orElseThrow(() -> new NotFoundException("Order " + orderId + " was not found."));
        if (!adminOrders.delete(orderId)) {
            throw new NotFoundException("Order " + orderId + " was not found.");
        }
    }
}
