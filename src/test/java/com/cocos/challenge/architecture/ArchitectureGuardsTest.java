package com.cocos.challenge.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTag;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;

/**
 * Closes two loopholes of {@link ArchitectureTest} (Constitution V.3 and V.6, v1.3.7). Do not modify, weaken or
 * disable.
 */
@ArchTag("unit")
@AnalyzeClasses(packages = "com.cocos.challenge", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureGuardsTest {

    private static final String DOMAIN_PACKAGE = "com.cocos.challenge.domain";

    // Rule 3: the @Transactional rules select use cases by the name of their inbound port, so every inbound port
    // must be named *UseCase (changes data) or *Query (read only); otherwise a service escapes both rules.
    @ArchTest
    static final ArchRule inbound_ports_are_named_use_case_or_query = classes()
            .that().resideInAPackage("com.cocos.challenge.application.port.in..")
            .and().areInterfaces()
            .should().haveSimpleNameEndingWith("UseCase")
            .orShould().haveSimpleNameEndingWith("Query")
            .because("Constitution V.3 / TEC-14: the transactional rules find use cases by the port name")
            .allowEmptyShould(true);

    // Rule 6: the raw-type check of ArchitectureTest misses domain objects inside generics
    // (ResponseEntity<Order>, List<Money>, Optional<Portfolio>).
    @ArchTest
    static final ArchRule controllers_never_return_domain_objects_inside_generics = methods()
            .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .and().arePublic()
            .should(notInvolveDomainTypesInTheReturnType())
            .because("Constitution V.6: domain objects are never serialized directly")
            .allowEmptyShould(true);

    private static ArchCondition<JavaMethod> notInvolveDomainTypesInTheReturnType() {
        return new ArchCondition<>("not involve domain types in the return type") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                List<String> domainTypes = method.getReturnType().getAllInvolvedRawTypes().stream()
                        .map(JavaClass::getName)
                        .filter(name -> name.startsWith(DOMAIN_PACKAGE + "."))
                        .sorted()
                        .toList();
                boolean satisfied = domainTypes.isEmpty();
                String message = method.getFullName() + (satisfied
                        ? " returns no domain type"
                        : " returns domain types " + domainTypes);
                events.add(new SimpleConditionEvent(method, satisfied, message));
            }
        };
    }
}
