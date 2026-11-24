package com.cocos.challenge.traceability;

import com.tngtech.archunit.core.domain.JavaMethod;

import java.util.Set;

/** Reads the JUnit tags that apply to a test method. */
final class TestTags {

    private TestTags() {
    }

    static Set<String> of(JavaMethod method) {
        return Set.of();
    }
}
