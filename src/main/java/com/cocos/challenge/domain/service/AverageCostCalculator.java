package com.cocos.challenge.domain.service;

import com.cocos.challenge.domain.model.Fill;
import com.cocos.challenge.domain.model.OrderSide;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Optional;

public final class AverageCostCalculator {

    private AverageCostCalculator() {
    }

    public static Optional<BigDecimal> averageCost(List<Fill> fillsInExecutionOrder) {
        long quantity = 0;
        BigDecimal average = null;
        for (Fill fill : fillsInExecutionOrder) {
            if (fill.side() == OrderSide.BUY) {
                BigDecimal price = fill.price().amount();
                long nextQuantity = quantity + fill.quantity();
                // DEC-02.1: average against the shares still held, or restart at the buy price.
                if (quantity > 0 && average != null) {
                    BigDecimal retainedCost = average.multiply(BigDecimal.valueOf(quantity));
                    BigDecimal boughtCost = price.multiply(BigDecimal.valueOf(fill.quantity()));
                    average = retainedCost.add(boughtCost)
                            .divide(BigDecimal.valueOf(nextQuantity), MathContext.DECIMAL128);
                } else {
                    average = price;
                }
                quantity = nextQuantity;
            } else {
                quantity -= fill.quantity();
                if (quantity == 0) {
                    average = null;
                }
            }
        }
        return Optional.ofNullable(average);
    }
}
