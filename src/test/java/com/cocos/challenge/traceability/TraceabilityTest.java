package com.cocos.challenge.traceability;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class TraceabilityTest {

    @Test
    void everyScenarioInScopeIsTraceable() throws IOException {
        String useCases = Files.readString(Path.of("docs/casos-de-uso.md"));
        Map<String, String> features = readFeatures();
        Set<String> scope = Files.readAllLines(Path.of("src/test/resources/traceability/scope.txt")).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .collect(java.util.stream.Collectors.toSet());
        Map<String, Set<String>> testElements = readTestElements();

        var violations = new TraceabilityChecker().check(useCases, features, testElements, scope);

        assertThat(violations)
                .withFailMessage("Traceability violations:%n%s", String.join("\n", violations))
                .isEmpty();
    }

    private static Map<String, String> readFeatures() throws IOException {
        Map<String, String> features = new HashMap<>();
        try (var paths = Files.walk(Path.of("src/test/resources/features"))) {
            for (Path path : paths.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".feature"))
                    .sorted().toList()) {
                features.put(path.toString(), Files.readString(path));
            }
        }
        return features;
    }

    private static Map<String, Set<String>> readTestElements() {
        var classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages("com.cocos.challenge");
        Map<String, Set<String>> elements = new HashMap<>();
        for (JavaClass javaClass : classes) {
            for (JavaMethod method : javaClass.getMethods()) {
                if (isTest(method)) {
                    elements.put(method.getFullName(), TestTags.of(method));
                }
            }
        }
        return elements;
    }

    private static boolean isTest(JavaMethod method) {
        return method.isAnnotatedWith(Test.class)
                || method.isAnnotatedWith(ParameterizedTest.class)
                || method.isAnnotatedWith(RepeatedTest.class)
                || method.isAnnotatedWith(TestFactory.class);
    }
}
