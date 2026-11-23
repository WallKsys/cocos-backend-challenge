package com.cocos.challenge.domain.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@Tag("unit")
class PercentageTest {

    @Test
    void normalizesToScaleTwoWithHalfUpRounding() {
        Percentage percentage = new Percentage(new BigDecimal("0.439"));

        assertThat(percentage.value()).isEqualByComparingTo("0.44");
        assertThat(percentage.value().scale()).isEqualTo(2);
        assertThat(new Percentage(new BigDecimal("-1.235")).value()).isEqualByComparingTo("-1.24");
    }

    @Test
    void equivalentValuesHaveTheSameRecordValue() {
        assertThat(new Percentage(new BigDecimal("0.4")))
                .isEqualTo(new Percentage(new BigDecimal("0.40")));
    }

    @Test
    void rejectsNullValue() {
        assertThatNullPointerException().isThrownBy(() -> new Percentage(null));
    }
}
