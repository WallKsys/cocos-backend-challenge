package com.cocos.challenge.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTag;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/** Internal-tool boundaries of spec 003 (TEC-18); ArchitectureTest remains untouched. */
@ArchTag("unit")
@AnalyzeClasses(packages = "com.cocos.challenge", importOptions = ImportOption.DoNotIncludeTests.class)
class InternalToolsArchitectureTest {

    @ArchTest
    static final ArchRule internal_controllers_are_conditional = classes()
            .that().resideInAPackage("..adapter.in.rest.internal..")
            .and().areAnnotatedWith(RestController.class)
            .should().beAnnotatedWith(ConditionalOnProperty.class)
            .because("internal controllers must not exist unless cocos.internal-tools.enabled=true")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule only_internal_controllers_map_internal_paths = classes()
            .that().areAnnotatedWith(RestController.class)
            .and().resideOutsideOfPackage("..adapter.in.rest.internal..")
            .should(notMapInternalPaths())
            .because("only internal controllers guarded by the admin interceptor may use /api/internal")
            .allowEmptyShould(true);

    private static ArchCondition<JavaClass> notMapInternalPaths() {
        return new ArchCondition<>("not have a class-level @RequestMapping starting with /api/internal") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean internal = javaClass.tryGetAnnotationOfType(RequestMapping.class)
                        .map(mapping -> Stream.concat(Arrays.stream(mapping.value()), Arrays.stream(mapping.path()))
                                .anyMatch(path -> path.startsWith("/api/internal")))
                        .orElse(false);
                events.add(new SimpleConditionEvent(javaClass, !internal,
                        javaClass.getName() + (internal ? " maps " : " does not map ") + "/api/internal"));
            }
        };
    }
}
