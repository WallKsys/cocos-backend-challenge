package com.cocos.challenge.domain.model;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;
import java.util.Optional;

public record MarketQuote(Money close, Money previousClose) {

    public MarketQuote {
        Objects.requireNonNull(close, "close");
    }

    public Optional<Percentage> dailyChange() {
        if (previousClose == null || previousClose.amount().signum() == 0) {
            return Optional.empty();
        }
        BigDecimal change = close.amount().subtract(previousClose.amount(), MathContext.DECIMAL128)
                .divide(previousClose.amount(), MathContext.DECIMAL128)
                .multiply(BigDecimal.valueOf(100), MathContext.DECIMAL128);
        return Optional.of(new Percentage(change));
    }
}
