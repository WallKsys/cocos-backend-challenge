package com.cocos.challenge.domain.model;

public record Fill(OrderSide side, long quantity, Money price) {
}
