package com.cocos.challenge.traceability;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The traceability check must see the same tags JUnit sees: direct, repeated, from meta-annotations (such as
 * {@code @IntegrationTest}) and inherited from superclasses.
 */
@Tag("unit")
class TestTagsTest {

    private static final JavaClasses FIXTURES = new ClassFileImporter().importClasses(
            DirectTags.class, MetaTagged.class, NestedMetaTagged.class, Subclass.class,
            IntegrationLike.class, OuterMeta.class);

    @Test
    void readsDirectTagsOnMethodAndClass() {
        assertThat(TestTags.of(method(DirectTags.class))).containsExactlyInAnyOrder("unit", "CU-02.01", "CU-02.02");
    }

    @Test
    void readsTagsFromMetaAnnotationsOnTheClass() {
        assertThat(TestTags.of(method(MetaTagged.class))).containsExactlyInAnyOrder("integration", "CU-02.01");
    }

    @Test
    void readsTagsFromMetaAnnotationsOfMetaAnnotations() {
        assertThat(TestTags.of(method(NestedMetaTagged.class))).containsExactlyInAnyOrder("integration", "outer");
    }

    @Test
    void readsTagsInheritedFromSuperclasses() {
        assertThat(TestTags.of(method(Subclass.class))).containsExactlyInAnyOrder("integration", "CU-02.03");
    }

    private static JavaMethod method(Class<?> owner) {
        return FIXTURES.get(owner).getMethod("example");
    }

    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Tag("integration")
    @interface IntegrationLike {
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @IntegrationLike
    @Tag("outer")
    @interface OuterMeta {
    }

    @Tag("unit")
    static class DirectTags {
        @Tag("CU-02.01")
        @Tag("CU-02.02")
        void example() {
        }
    }

    @IntegrationLike
    static class MetaTagged {
        @Tag("CU-02.01")
        void example() {
        }
    }

    @OuterMeta
    static class NestedMetaTagged {
        void example() {
        }
    }

    @IntegrationLike
    abstract static class Base {
    }

    static class Subclass extends Base {
        @Tag("CU-02.03")
        void example() {
        }
    }
}
