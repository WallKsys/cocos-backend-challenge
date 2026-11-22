package com.cocos.challenge.domain.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Spec 003 / DEC-07 and DEC-09: request fingerprints are SHA-256 hashes in canonical lowercase hex form.
 */
@Tag("unit")
class RequestFingerprintTest {

    private static final String ABC_SHA256 =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Test
    void hashesTheCanonicalRequestWithSha256() {
        assertThat(RequestFingerprint.of("abc").hex()).isEqualTo(ABC_SHA256);
    }

    @Test
    void acceptsExactly64LowercaseHexCharacters() {
        assertThat(new RequestFingerprint(ABC_SHA256).hex()).isEqualTo(ABC_SHA256);
    }

    @ParameterizedTest
    @MethodSource("invalidHexValues")
    void rejectsAnythingOtherThan64LowercaseHexCharacters(String hex) {
        assertThatIllegalArgumentException().isThrownBy(() -> new RequestFingerprint(hex));
    }

    static Stream<String> invalidHexValues() {
        return Stream.of(
                null,
                "",
                "a".repeat(63),
                "a".repeat(65),
                "A".repeat(64),
                "g".repeat(64),
                "a".repeat(63) + " ");
    }
}
