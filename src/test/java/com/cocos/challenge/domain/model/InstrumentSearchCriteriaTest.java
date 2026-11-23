package com.cocos.challenge.domain.model;

import com.cocos.challenge.domain.exception.InvalidRequestException;
import com.cocos.challenge.domain.exception.SearchTermTooShortException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@Tag("unit")
@Tag("CU-02.05b")
@Tag("CU-02.13")
class InstrumentSearchCriteriaTest {

    @Test
    void appliesDefaultsToAbsentParameters() {
        InstrumentSearchCriteria criteria = InstrumentSearchCriteria.of(null, null, null, null, null);

        assertThat(criteria.q()).isNull();
        assertThat(criteria.ticker()).isNull();
        assertThat(criteria.name()).isNull();
        assertThat(criteria.limit()).isEqualTo(20);
        assertThat(criteria.cursor()).isNull();
    }

    @Test
    void buildsTrimmedTermsAndDecodesTheCursor() {
        SearchCursor cursor = new SearchCursor(1, "PAMP");

        InstrumentSearchCriteria criteria = InstrumentSearchCriteria.of(
                " banco ", " PAMP ", " galicia ", 100, cursor.encode());

        assertThat(criteria.q().text()).isEqualTo("banco");
        assertThat(criteria.ticker().text()).isEqualTo("PAMP");
        assertThat(criteria.name().text()).isEqualTo("galicia");
        assertThat(criteria.limit()).isEqualTo(100);
        assertThat(criteria.cursor()).isEqualTo(cursor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"b", "ba", "😀", "😀😀"})
    void shortGeneralTermsAreAllowedAndMarkedShort(String q) {
        assertThat(InstrumentSearchCriteria.of(q, null, null, null, null).q().isShort()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"b", "ba", "😀", "😀😀"})
    void rejectsShortNameFilters(String name) {
        assertThatExceptionOfType(SearchTermTooShortException.class)
                .isThrownBy(() -> InstrumentSearchCriteria.of(null, null, name, null, null))
                .withMessage("The name filter requires at least 3 characters.");
    }

    @Test
    void acceptsANameWithThreeUnicodeCodePoints() {
        assertThat(InstrumentSearchCriteria.of(null, null, "😀😀😀", null, null).name().isShort()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101, -1})
    void rejectsLimitsOutsideTheAllowedRange(int limit) {
        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> InstrumentSearchCriteria.of(null, null, null, limit, null))
                .withMessage("Request validation failed: 'limit' must be between 1 and 100.");
    }

    @Test
    void acceptsTheLowerLimitAndTreatsBlankCursorAsAbsent() {
        InstrumentSearchCriteria criteria = InstrumentSearchCriteria.of(null, null, null, 1, " ");

        assertThat(criteria.limit()).isEqualTo(1);
        assertThat(criteria.cursor()).isNull();
    }
}
