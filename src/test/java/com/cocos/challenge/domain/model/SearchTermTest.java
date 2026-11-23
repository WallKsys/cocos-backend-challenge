package com.cocos.challenge.domain.model;

import com.cocos.challenge.domain.exception.InvalidRequestException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@Tag("unit")
class SearchTermTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void nullOrBlankRawValueProducesNoSearchTerm(String raw) {
        assertThat(SearchTerm.of("q", raw)).isNull();
    }

    @Test
    void trimsTheSearchTerm() {
        assertThat(SearchTerm.of("q", "  PAMP\t").text()).isEqualTo("PAMP");
    }

    @Test
    void acceptsOneHundredUnicodeCodePoints() {
        String raw = "😀".repeat(100);

        assertThat(SearchTerm.of("q", raw).text()).isEqualTo(raw);
    }

    @ParameterizedTest
    @ValueSource(strings = {"q", "ticker", "name"})
    void rejectsMoreThanOneHundredCodePointsWithTheParameterName(String parameterName) {
        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> SearchTerm.of(parameterName, "😀".repeat(101)))
                .withMessage("Request validation failed: '%s' must have at most 100 characters.", parameterName);
    }

    @Test
    void shortnessCountsUnicodeCodePoints() {
        assertThat(SearchTerm.of("q", "ab").isShort()).isTrue();
        assertThat(SearchTerm.of("q", "😀😀").isShort()).isTrue();
        assertThat(SearchTerm.of("q", "abc").isShort()).isFalse();
        assertThat(SearchTerm.of("q", "😀😀😀").isShort()).isFalse();
    }
}
