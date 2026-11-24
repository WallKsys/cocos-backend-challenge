package com.cocos.challenge.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaType;
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
 * Closes three loopholes of {@link ArchitectureTest} (Constitution V.3 and V.6, v1.3.7). Do not modify, weaken or
 * disable.
 */
@ArchTag("unit")
@AnalyzeClasses(packages = "com.cocos.challenge", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureGuardsTest {

    private static final String DOMAIN_PACKAGE = "com.cocos.challenge.domain";
    private static final String REST = "com.cocos.challenge.infrastructure.adapter.in.rest..";

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
            .should(new ArchCondition<JavaMethod>("not involve domain types in the return type") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    report(method.getFullName(), method.getReturnType(), method, events);
                }
            })
            .because("Constitution V.6: domain objects are never serialized directly")
            .allowEmptyShould(true);

    // Rule 6: a REST DTO (a record of the REST adapter, request or response) never holds a domain type, not even an
    // enum: Jackson would serialize or deserialize the domain model, its names would become the API contract, and
    // request errors would carry Jackson's messages instead of the catalog's.
    @ArchTest
    static final ArchRule rest_dtos_do_not_hold_domain_types = fields()
            .that().areDeclaredInClassesThat().resideInAPackage(REST)
            .and().areDeclaredInClassesThat().areRecords()
            .and().areNotStatic()
            .should(new ArchCondition<JavaField>("not involve domain types in the field type") {
                @Override
                public void check(JavaField field, ConditionEvents events) {
                    report(field.getFullName(), field.getType(), field, events);
                }
            })
            .because("Constitution V.6: DTOs carry JDK types and other DTOs; the domain model never crosses the JSON")
            .allowEmptyShould(true);

    private static void report(String name, JavaType type, Object location, ConditionEvents events) {
        List<String> domainTypes = type.getAllInvolvedRawTypes().stream()
                .map(JavaClass::getName)
                .filter(className -> className.startsWith(DOMAIN_PACKAGE + "."))
                .sorted()
                .toList();
        boolean satisfied = domainTypes.isEmpty();
        String message = name + (satisfied ? " involves no domain type" : " involves domain types " + domainTypes);
        events.add(new SimpleConditionEvent(location, satisfied, message));
    }
}
