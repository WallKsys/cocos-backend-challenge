package com.cocos.challenge.domain.model;

public record Fill(OrderSide side, long quantity, Money price) {

    public Fill {
        if (side != OrderSide.BUY && side != OrderSide.SELL) {
            throw new IllegalArgumentException("Fill side must be BUY or SELL.");
        }
    }
}
