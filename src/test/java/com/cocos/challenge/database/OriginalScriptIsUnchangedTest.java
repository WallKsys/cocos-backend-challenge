package com.cocos.challenge.database;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 002 US1 scenario 5: the first Flyway migration is the provided script, byte for byte.
 */
@Tag("unit")
class OriginalScriptIsUnchangedTest {

    private static final Path PROVIDED_SCRIPT = Path.of("docs/referencia/database.sql");
    private static final Path V1_MIGRATION =
            Path.of("src/main/resources/db/migration/V1__original_schema_and_seed.sql");

    @Test
    void v1MigrationIsByteIdenticalToTheProvidedScript() throws IOException {
        byte[] provided = Files.readAllBytes(PROVIDED_SCRIPT);
        byte[] migration = Files.readAllBytes(V1_MIGRATION);

        assertThat(migration).isEqualTo(provided);
    }
}
