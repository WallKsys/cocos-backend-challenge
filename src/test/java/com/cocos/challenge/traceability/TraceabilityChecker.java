package com.cocos.challenge.traceability;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TraceabilityChecker {

    private static final String ID = "CU-(?:\\d{2}|I)\\.\\d{2}[a-z]?";
    private static final Pattern DOCUMENTED_ID = Pattern.compile("^\\| (" + ID + ")(?=\\s|\\|)");
    private static final Pattern FEATURE_TAG = Pattern.compile("@(" + ID + ")");
    private static final Pattern TEST_TAG = Pattern.compile(ID);

    List<String> check(String useCases, Map<String, String> features,
            Map<String, Set<String>> testElements, Set<String> scopeGroups) {
        List<String> violations = new ArrayList<>();
        List<String> documentedIds = documentedIds(useCases);
        Set<String> knownIds = new HashSet<>(documentedIds);
        findDuplicateDocumentedIds(documentedIds, violations);

        Map<String, Integer> scenarioOccurrences = new HashMap<>();
        for (var feature : new TreeMap<>(features).entrySet()) {
            parseFeature(feature.getKey(), feature.getValue(), knownIds, scenarioOccurrences, violations);
        }
        scenarioOccurrences.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> violations.add(entry.getKey() + ": appears in more than one scenario (rule 6)"));

        for (String id : documentedIds) {
            if (scopeGroups.stream().noneMatch(group -> id.startsWith(group + "."))) {
                continue;
            }
            if (!scenarioOccurrences.containsKey(id)) {
                violations.add(id + ": in scope but no Gherkin scenario is tagged \"" + id + "\" (rule 7)");
            }
            List<Map.Entry<String, Set<String>>> matches = testElements.entrySet().stream()
                    .filter(test -> test.getValue().contains(id))
                    .sorted(Map.Entry.comparingByKey())
                    .toList();
            if (matches.isEmpty()) {
                violations.add(id + ": in scope but no JUnit test is tagged \"" + id + "\" (rule 8)");
            } else if (matches.stream().noneMatch(test -> test.getValue().contains("unit")
                    || test.getValue().contains("integration"))) {
                violations.add(id + ": in scope but its JUnit tests " + matches.stream().map(Map.Entry::getKey).toList()
                        + " are not tagged unit or integration (rule 9)");
            }
        }

        for (var test : new TreeMap<>(testElements).entrySet()) {
            for (String tag : test.getValue()) {
                if (TEST_TAG.matcher(tag).matches() && !knownIds.contains(tag)) {
                    violations.add(test.getKey() + ": JUnit test tag @" + tag
                            + " is not an ID in docs/casos-de-uso.md (rule 10)");
                }
            }
        }
        return violations;
    }

    private static List<String> documentedIds(String useCases) {
        List<String> ids = new ArrayList<>();
        for (String line : useCases.split("\\R")) {
            Matcher matcher = DOCUMENTED_ID.matcher(line);
            if (line.startsWith("| CU-") && matcher.find()) {
                ids.add(matcher.group(1));
            }
        }
        return ids;
    }

    private static void findDuplicateDocumentedIds(List<String> ids, List<String> violations) {
        Map<String, Integer> occurrences = new LinkedHashMap<>();
        ids.forEach(id -> occurrences.merge(id, 1, Integer::sum));
        occurrences.forEach((id, count) -> {
            if (count > 1) {
                violations.add(id + ": appears more than once in docs/casos-de-uso.md (rule 1)");
            }
        });
    }

    private static void parseFeature(String path, String text, Set<String> knownIds,
            Map<String, Integer> scenarioOccurrences, List<String> violations) {
        String[] lines = text.split("\\R", -1);
        boolean smokeFeature = false;
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim();
            if (line.startsWith("Feature:")) {
                List<String> tags = tagsAbove(lines, index);
                smokeFeature = tags.contains("@smoke");
                for (String id : cuTags(tags)) {
                    checkKnown(path, id, knownIds, violations);
                    violations.add(path + ": @" + id + " is a CU tag at feature level (rule 3)");
                }
            } else if (isScenario(line)) {
                String name = line.substring(line.indexOf(':') + 1).trim();
                List<String> tags = tagsAbove(lines, index);
                List<String> ids = cuTags(tags);
                if (ids.size() > 1) {
                    violations.add(path + ", scenario '" + name + "': has multiple CU tags " + ids + " (rule 4)");
                } else if (ids.isEmpty() && !smokeFeature && !tags.contains("@smoke")) {
                    violations.add(path + ", scenario '" + name + "': has no CU tag and is not tagged @smoke (rule 5)");
                }
                for (String id : ids) {
                    checkKnown(path, id, knownIds, violations);
                    scenarioOccurrences.merge(id, 1, Integer::sum);
                }
            }
        }
    }

    private static boolean isScenario(String line) {
        return line.startsWith("Scenario:") || line.startsWith("Scenario Outline:") || line.startsWith("Example:");
    }

    private static List<String> tagsAbove(String[] lines, int index) {
        List<String> tags = new ArrayList<>();
        for (int current = index - 1; current >= 0; current--) {
            String line = lines[current].trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (!line.startsWith("@")) {
                break;
            }
            tags.addAll(Arrays.asList(line.split("\\s+")));
        }
        return tags;
    }

    private static List<String> cuTags(List<String> tags) {
        List<String> ids = new ArrayList<>();
        for (String tag : tags) {
            Matcher matcher = FEATURE_TAG.matcher(tag);
            if (matcher.matches()) {
                ids.add(matcher.group(1));
            }
        }
        return ids;
    }

    private static void checkKnown(String path, String id, Set<String> knownIds, List<String> violations) {
        if (!knownIds.contains(id)) {
            violations.add(path + ": @" + id + " is not an ID in docs/casos-de-uso.md (rule 2)");
        }
    }
}
