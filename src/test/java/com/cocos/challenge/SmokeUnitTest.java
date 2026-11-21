package com.cocos.challenge;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Canary for {@code ./mvnw test -Dgroups=unit}: proves unit tests run without Spring and without Docker.
 */
@Tag("unit")
class SmokeUnitTest {

    @Test
    void unitTestsRun() {
        assertThat(1 + 1).isEqualTo(2);
    }
}
