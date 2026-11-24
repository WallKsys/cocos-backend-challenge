package com.cocos.challenge.domain.service;

import static com.cocos.challenge.domain.model.OrderSide.BUY;
import static com.cocos.challenge.domain.model.OrderSide.SELL;
import static org.assertj.core.api.Assertions.assertThat;

import com.cocos.challenge.domain.model.Fill;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.domain.model.OrderSide;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class AverageCostCalculatorTest {

    @Test
    @Tag("CU-01.12")
    void averagesTheNewBuyWithTheSharesStillHeld() {
        assertCost("929.17", fill(BUY, 50, "930"), fill(SELL, 10, "925.85"), fill(BUY, 10, "925.85"));
    }

    @Test
    @Tag("CU-01.13")
    void clearsAverageAtZeroAndStartsAgainOnTheNextBuy() {
        Fill buy = fill(BUY, 50, "930");
        Fill sell = fill(SELL, 50, "925.85");
        assertThat(AverageCostCalculator.averageCost(List.of(buy, sell))).isEmpty();
        assertCost("1000", buy, sell, fill(BUY, 10, "1000"));
    }

    @Test
    @Tag("CU-01.14")
    void processesFillsInTheSuppliedExecutionOrder() {
        Fill earlyBuy = fill(BUY, 10, "100");
        Fill sell = fill(SELL, 10, "120");
        Fill lateBuy = fill(BUY, 5, "200");
        assertCost("200", earlyBuy, sell, lateBuy);
        assertCost("133.3333333333333333333333333333333", lateBuy, earlyBuy, sell);
    }

    @Test
    void restartsOnABuyWhileQuantityIsNegative() {
        Fill buy = fill(BUY, 20, "1540");
        Fill sell = fill(SELL, 30, "1500");
        assertCost("1540", buy, sell);
        assertCost("1600", buy, sell, fill(BUY, 11, "1600"));
        assertThat(AverageCostCalculator.averageCost(List.of(sell))).isEmpty();
    }

    private static void assertCost(String expected, Fill... fills) {
        assertThat(AverageCostCalculator.averageCost(List.of(fills))).hasValueSatisfying(
                cost -> assertThat(cost).isEqualByComparingTo(new BigDecimal(expected)));
    }

    private static Fill fill(OrderSide side, long quantity, String price) {
        return new Fill(side, quantity, Money.of(price));
    }
}
