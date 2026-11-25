package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.DeleteOrderUseCase;
import com.cocos.challenge.application.port.out.AccountLock;
import com.cocos.challenge.application.port.out.AdminOrderRepository;
import org.springframework.transaction.annotation.Transactional;

public class DeleteOrderService implements DeleteOrderUseCase {

    private final AccountLock lock;
    private final AdminOrderRepository adminOrders;

    public DeleteOrderService(AccountLock lock, AdminOrderRepository adminOrders) {
        this.lock = lock;
        this.adminOrders = adminOrders;
    }

    @Override
    @Transactional
    public void delete(long orderId) {
        throw new UnsupportedOperationException();
    }
}
