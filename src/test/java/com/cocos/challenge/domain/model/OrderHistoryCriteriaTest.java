package com.cocos.challenge.domain.model;

import com.cocos.challenge.domain.exception.InvalidRequestException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class OrderHistoryCriteriaTest {

    @Test
    void defaultsLimitAndTreatsBlankStatusAsAbsent() {
        assertThat(OrderHistoryCriteria.of(null, null, null)).isEqualTo(new OrderHistoryCriteria(null, 20, null));
        assertThat(OrderHistoryCriteria.of("  ", 100, null)).isEqualTo(new OrderHistoryCriteria(null, 100, null));
    }

    @Test
    void parsesStatusAndCursor() {
        HistoryCursor cursor = new HistoryCursor(Instant.EPOCH, 7);
        assertThat(OrderHistoryCriteria.of("NEW", 1, cursor.encode()))
                .isEqualTo(new OrderHistoryCriteria(OrderStatus.NEW, 1, cursor));
    }

    @Test
    void rejectsInvalidFilters() {
        assertThatThrownBy(() -> OrderHistoryCriteria.of("PENDING", null, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Request validation failed: 'status' must be one of NEW, FILLED, REJECTED, CANCELLED.");
        assertThatThrownBy(() -> OrderHistoryCriteria.of(null, 101, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Request validation failed: 'limit' must be between 1 and 100.");
        assertThatThrownBy(() -> OrderHistoryCriteria.of(null, null, "%"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Request validation failed: 'cursor' is invalid.");
    }
}
