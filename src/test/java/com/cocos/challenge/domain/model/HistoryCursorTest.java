package com.cocos.challenge.domain.model;

import com.cocos.challenge.domain.exception.InvalidRequestException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class HistoryCursorTest {

    @Test
    @Tag("CU-07.07")
    void roundTripsUtcMicrosecondsAndOrderId() {
        HistoryCursor cursor = new HistoryCursor(Instant.parse("1969-12-31T23:59:59.999999Z"), 7);

        assertThat(cursor.encode()).isEqualTo(encode("v1:-1:7"));
        assertThat(HistoryCursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @Test
    void rejectsMalformedCursors() {
        for (String value : new String[]{"%", encode("v2:0:7"), encode("v1:no:7"), encode("v1:0:0")}) {
            assertThatThrownBy(() -> HistoryCursor.decode(value))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessage("Request validation failed: 'cursor' is invalid.");
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
