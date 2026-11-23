package com.cocos.challenge.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Percentage(BigDecimal value) {

    public Percentage {
        value = Objects.requireNonNull(value, "value").setScale(2, RoundingMode.HALF_UP);
    }
}
