package com.cocos.challenge.infrastructure.adapter.in.rest.format;

import com.cocos.challenge.domain.model.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class ApiDecimalsTest {

    @Test
    void formatUsesTwoDecimalsAndHalfUpRounding() {
        assertThat(ApiDecimals.format(new BigDecimal("925.855"))).isEqualTo("925.86");
        assertThat(ApiDecimals.format(new BigDecimal("50000"))).isEqualTo("50000.00");
        assertThat(ApiDecimals.format(new BigDecimal("-0.45"))).isEqualTo("-0.45");
        assertThat(ApiDecimals.format(new BigDecimal("1E+3"))).isEqualTo("1000.00");
        assertThat(ApiDecimals.format(Money.of("925.8"))).isEqualTo("925.80");
    }

    @Test
    void parseAndFormatNormalizeAcceptedInputScales() {
        assertThat(ApiDecimals.format(ApiDecimals.parse("925.8"))).isEqualTo("925.80");
        assertThat(ApiDecimals.format(ApiDecimals.parse("925"))).isEqualTo("925.00");
    }
}
