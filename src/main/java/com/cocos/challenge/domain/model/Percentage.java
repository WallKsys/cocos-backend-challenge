package com.cocos.challenge.domain.model;

import java.math.BigDecimal;

public record Percentage(BigDecimal value) {

    public Percentage {
        throw new UnsupportedOperationException();
    }
}
