package com.cocos.challenge.domain.model;

import java.util.Optional;

public record MarketQuote(Money close, Money previousClose) {

    public Optional<Percentage> dailyChange() {
        throw new UnsupportedOperationException();
    }
}
