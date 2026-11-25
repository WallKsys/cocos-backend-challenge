package com.cocos.challenge.domain.exception;

import com.cocos.challenge.domain.model.OrderStatus;

public class OrderNotExecutableException extends DomainException {

    public OrderNotExecutableException(long orderId, OrderStatus status) {
        super("Order " + orderId + " cannot be executed because its status is " + status
                + ". Only NEW orders can be executed.");
    }
}
