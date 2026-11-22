package com.cocos.challenge.domain.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Spec 003: money is exact, immutable and normalized to two decimals with HALF_UP rounding.
 */
@Tag("unit")
class MoneyTest {

    @Test
    void constructorAndFactoriesNormalizeToScaleTwo() {
        assertThat(new Money(new BigDecimal("925.8")).amount()).isEqualByComparingTo("925.80");
        assertThat(new Money(new BigDecimal("925.8")).amount().scale()).isEqualTo(2);
        assertThat(Money.of("925").amount()).isEqualByComparingTo("925.00");
        assertThat(Money.of(new BigDecimal("925.80"))).isEqualTo(Money.of("925.8"));
    }

    @Test
    void normalizationRoundsHalfUp() {
        assertThat(Money.of("925.855").amount()).isEqualByComparingTo("925.86");
        assertThat(Money.of("-925.855").amount()).isEqualByComparingTo("-925.86");
    }

    @Test
    void equivalentValuesWithDifferentInputScalesAreEqual() {
        assertThat(Money.of("925.8")).isEqualTo(Money.of("925.80"));
        assertThat(Money.of("925.8").hashCode()).isEqualTo(Money.of("925.80").hashCode());
    }

    @Test
    void nullAmountIsRejected() {
        assertThatNullPointerException().isThrownBy(() -> new Money(null));
        assertThatNullPointerException().isThrownBy(() -> Money.of((BigDecimal) null));
    }

    @Test
    void zeroIsNormalizedMoney() {
        assertThat(Money.ZERO).isEqualTo(Money.of("0"));
        assertThat(Money.ZERO.amount().scale()).isEqualTo(2);
    }

    @Test
    void additionAndSubtractionProduceNormalizedMoney() {
        assertThat(Money.of("10.10").plus(Money.of("2.005")).amount()).isEqualByComparingTo("12.11");
        assertThat(Money.of("10.10").minus(Money.of("2.005")).amount()).isEqualByComparingTo("8.09");
    }

    @Test
    void multiplicationRoundsHalfUpToScaleTwo() {
        Money result = Money.of("1.00").times(new BigDecimal("0.125"));

        assertThat(result.amount()).isEqualByComparingTo("0.13");
        assertThat(result.amount().scale()).isEqualTo(2);
    }

    @Test
    void comparisonsUseTheMonetaryValue() {
        assertThat(Money.of("-0.01").isNegative()).isTrue();
        assertThat(Money.ZERO.isNegative()).isFalse();
        assertThat(Money.of("10.00").isGreaterThan(Money.of("9.99"))).isTrue();
        assertThat(Money.of("9.99").isGreaterThan(Money.of("10.00"))).isFalse();
        assertThat(Money.of("10.00").compareTo(Money.of("9.99"))).isPositive();
        assertThat(Money.of("9.99").compareTo(Money.of("10.00"))).isNegative();
        assertThat(Money.of("10.0").compareTo(Money.of("10.00"))).isZero();
    }
}
