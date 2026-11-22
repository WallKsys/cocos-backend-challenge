package com.cocos.challenge.domain.model;

import com.cocos.challenge.domain.exception.InvalidRequestException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Spec 003: a user id is always a positive value supported by the integer database column.
 */
@Tag("unit")
class UserIdTest {

    @ParameterizedTest
    @ValueSource(longs = {1, 2, Integer.MAX_VALUE})
    void acceptsValuesWithinTheDatabaseRange(long value) {
        assertThat(new UserId(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, (long) Integer.MAX_VALUE + 1, Long.MAX_VALUE})
    void rejectsValuesOutsideTheDatabaseRange(long value) {
        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> new UserId(value));
    }
}
