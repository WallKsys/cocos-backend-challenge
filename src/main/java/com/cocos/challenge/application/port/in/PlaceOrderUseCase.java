package com.cocos.challenge.application.port.in;

public interface PlaceOrderUseCase {

    PlaceOrderResult place(PlaceOrderCommand command);
}
