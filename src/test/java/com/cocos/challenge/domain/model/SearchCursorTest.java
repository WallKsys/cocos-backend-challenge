package com.cocos.challenge.domain.model;

import com.cocos.challenge.domain.exception.InvalidRequestException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@Tag("unit")
class SearchCursorTest {

    @Test
    void encodesAsUrlSafeBase64WithoutPadding() {
        assertThat(new SearchCursor(1, "PAMP").encode()).isEqualTo("djE6MTpQQU1Q");
    }

    @Test
    void decodesAnEncodedCursor() {
        SearchCursor cursor = new SearchCursor(2, "😀");

        assertThat(SearchCursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @ParameterizedTest
    @MethodSource("invalidCursors")
    void rejectsInvalidCursors(String cursor) {
        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> SearchCursor.decode(cursor))
                .withMessage("Request validation failed: 'cursor' is invalid.");
    }

    static Stream<String> invalidCursors() {
        return Stream.of(
                null,
                "",
                "not base64",
                encoded("v2:1:PAMP"),
                encoded("v1:x:PAMP"),
                encoded("v1:-1:PAMP"),
                encoded("v1:3:PAMP"),
                encoded("v1:1:"),
                encoded("v1:1"));
    }

    private static String encoded(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
