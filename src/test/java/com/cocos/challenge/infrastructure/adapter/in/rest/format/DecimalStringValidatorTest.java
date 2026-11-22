package com.cocos.challenge.infrastructure.adapter.in.rest.format;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class DecimalStringValidatorTest {

    private final DecimalStringValidator validator = new DecimalStringValidator();

    @Test
    void acceptsNullAndDecimalStringsWithinTheLimits() {
        assertThat(validator.isValid(null, null)).isTrue();
        for (String value : List.of("0", "-0.45", "925", "925.8", "925.85", "9999999999999999.99")) {
            assertThat(validator.isValid(value, null)).as(value).isTrue();
        }
    }

    @Test
    void rejectsInvalidSyntaxScaleAndLength() {
        for (String value : List.of("", " 1.00", "1.00 ", "1e3", "12,5", "925.855", ".5", "1.",
                "99999999999999999")) {
            assertThat(validator.isValid(value, null)).as(value).isFalse();
        }
    }
}
