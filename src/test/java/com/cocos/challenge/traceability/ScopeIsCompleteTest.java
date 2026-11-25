package com.cocos.challenge.traceability;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class ScopeIsCompleteTest {

    private static final Pattern DOCUMENTED_GROUP =
            Pattern.compile("^\\| (CU-(?:\\d{2}|I))\\.\\d{2}[a-z]?(?=\\s|\\|)");

    @Test
    void traceabilityScopeCoversEveryDocumentedCuGroup() throws IOException {
        Set<String> documentedGroups = new LinkedHashSet<>();
        for (String line : Files.readAllLines(Path.of("docs/casos-de-uso.md"))) {
            Matcher matcher = DOCUMENTED_GROUP.matcher(line);
            if (line.startsWith("| CU-") && matcher.find()) {
                documentedGroups.add(matcher.group(1));
            }
        }
        Set<String> scope = Files.readAllLines(Path.of("src/test/resources/traceability/scope.txt")).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .collect(Collectors.toSet());

        assertThat(documentedGroups).isNotEmpty();
        assertThat(scope)
                .withFailMessage("Traceability scope %s must match documented CU groups %s", scope, documentedGroups)
                .containsExactlyInAnyOrderElementsOf(documentedGroups);
    }
}
