package com.cocos.challenge.traceability;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.properties.HasAnnotations;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;

import java.util.HashSet;
import java.util.Set;

/**
 * Reads the JUnit tags that apply to a test method, as JUnit does: on the method and its class, directly or through
 * meta-annotations (such as {@code @IntegrationTest}), and inherited from superclasses ({@code @Tag} is
 * {@code @Inherited}).
 */
final class TestTags {

    private TestTags() {
    }

    static Set<String> of(JavaMethod method) {
        Set<String> tags = new HashSet<>();
        collect(method, tags, new HashSet<>());
        JavaClass owner = method.getOwner();
        collect(owner, tags, new HashSet<>());
        for (JavaClass superclass : owner.getAllRawSuperclasses()) {
            collect(superclass, tags, new HashSet<>());
        }
        return tags;
    }

    private static void collect(HasAnnotations<?> element, Set<String> tags, Set<String> visited) {
        element.tryGetAnnotationOfType(Tag.class).ifPresent(tag -> tags.add(tag.value()));
        element.tryGetAnnotationOfType(Tags.class).ifPresent(container -> {
            for (Tag tag : container.value()) {
                tags.add(tag.value());
            }
        });
        for (JavaAnnotation<?> annotation : element.getAnnotations()) {
            JavaClass type = annotation.getRawType();
            String name = type.getName();
            if (!name.startsWith("java.") && !name.startsWith("org.junit.") && visited.add(name)) {
                collect(type, tags, visited);
            }
        }
    }
}
