package com.cocos.challenge.traceability;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class TraceabilityCheckerTest {

    private static final String ID = "CU-02.01";
    private final TraceabilityChecker checker = new TraceabilityChecker();

    @Test
    void rule1RejectsDuplicateIdsInUseCases() {
        assertOnlyRule(check(docs(ID, ID), Map.of(), Map.of(), Set.of()), 1, ID);
    }

    @Test
    void rule2RejectsUnknownFeatureIds() {
        assertOnlyRule(check(docs(), feature(scenario("CU-99.99")), Map.of(), Set.of()), 2, "CU-99.99");
    }

    @Test
    void rule3RejectsCuTagsAtFeatureLevel() {
        String body = "@" + ID + "\nFeature: fixture\n" + scenario(ID);
        assertOnlyRule(check(docs(ID), feature(body), Map.of(), Set.of()), 3, ID);
    }

    @Test
    void rule4RejectsSeveralCuTagsOnOneScenario() {
        String body = "Feature: fixture\n  @" + ID + " @CU-02.02\n  Scenario: too many tags\n";
        assertOnlyRule(check(docs(ID, "CU-02.02"), feature(body), Map.of(), Set.of()), 4, "too many tags");
    }

    @Test
    void rule5RejectsScenarioWithoutCuOrSmokeTag() {
        assertOnlyRule(check(docs(), feature("Feature: fixture\nScenario: missing tag\n"), Map.of(), Set.of()),
                5, "missing tag");
    }

    @Test
    void rule6RejectsAnIdOnMoreThanOneScenario() {
        assertOnlyRule(check(docs(ID), features(ID, ID), Map.of(), Set.of()), 6, ID);
    }

    @Test
    void rule7RejectsMissingScenarioWhenIdIsInScope() {
        assertOnlyRule(check(docs(ID), Map.of(), testedIds(ID), Set.of("CU-02")), 7, ID);
    }

    @Test
    void rule8RejectsMissingTestWhenIdIsInScope() {
        assertOnlyRule(check(docs(ID), features(ID), Map.of(), Set.of("CU-02")), 8, ID);
    }

    @Test
    void rule9RejectsTestsWithoutUnitOrIntegrationTag() {
        var tests = testWithTags(ID);
        assertOnlyRule(check(docs(ID), features(ID), tests, Set.of("CU-02")), 9, "ExampleTest.example");
    }

    @Test
    void rule10RejectsUnknownTestIds() {
        var tests = testWithTags("CU-99.99", "unit");
        assertOnlyRule(check(docs(), Map.of(), tests, Set.of()), 10, "CU-99.99");
    }

    @Test
    void passesWhenScopedIdHasOneScenarioAndProperlyTaggedTest() {
        assertThat(check(docs(ID), features(ID), testedIds(ID), Set.of("CU-02"))).isEmpty();
    }

    @Test
    void emptyScopePassesButStillRejectsUnknownFeatureIds() {
        String smoke = "@smoke\nFeature: fixture\nScenario: smoke\n";
        assertThat(check(docs(), feature(smoke), Map.of(), Set.of())).isEmpty();
        assertOnlyRule(check(docs(), features("CU-99.99"), Map.of(), Set.of()), 2, "CU-99.99");
    }

    @Test
    void recognizesSuffixedAndInternalScenarioIds() {
        var features = features("CU-02.05b", "CU-I.01");
        var tests = testedIds("CU-02.05b", "CU-I.01");
        assertThat(check(docs("CU-02.05b", "CU-I.01"), features, tests, Set.of("CU-02", "CU-I"))).isEmpty();
    }

    @Test
    void testWithCuAndIntegrationTagsSatisfiesRules8And9() {
        var tests = testWithTags(ID, "integration");
        assertThat(check(docs(ID), features(ID), tests, Set.of("CU-02"))).isEmpty();
    }

    private List<String> check(String docs, Map<String, String> features,
            Map<String, Set<String>> tests, Set<String> scope) {
        return checker.check(docs, features, tests, scope);
    }

    private static String docs(String... ids) {
        return java.util.Arrays.stream(ids).map(id -> "| " + id + " | fixture |\n").collect(joining());
    }

    private static String scenario(String id) {
        return "Feature: fixture\n  @" + id + "\n  Scenario: " + id + "\n";
    }

    private static Map<String, String> feature(String body) {
        return Map.of("fixture.feature", body);
    }

    private static Map<String, String> features(String... ids) {
        Map<String, String> features = new LinkedHashMap<>();
        for (int index = 0; index < ids.length; index++) {
            features.put("fixture-" + index + ".feature", scenario(ids[index]));
        }
        return features;
    }

    private static Map<String, Set<String>> testWithTags(String... tags) {
        return Map.of("ExampleTest.example", Set.of(tags));
    }

    private static Map<String, Set<String>> testedIds(String... ids) {
        Map<String, Set<String>> tests = new LinkedHashMap<>();
        for (String id : ids) {
            tests.put("ExampleTest." + id, Set.of(id, "unit"));
        }
        return tests;
    }

    private static void assertOnlyRule(List<String> violations, int rule, String expectedText) {
        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst()).contains(expectedText, "(rule " + rule + ")");
    }
}
