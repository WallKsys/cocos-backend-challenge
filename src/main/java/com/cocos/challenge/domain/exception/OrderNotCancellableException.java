package com.cocos.challenge.domain.exception;

import com.cocos.challenge.domain.model.OrderStatus;

public non-sealed class OrderNotCancellableException extends DomainException {

    public OrderNotCancellableException(long orderId, OrderStatus status) {
        super("Order " + orderId + " cannot be cancelled because its status is " + status
                + ". Only NEW orders can be cancelled.");
    }
}
