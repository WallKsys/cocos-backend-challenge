package com.cocos.challenge.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTag;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.type;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Architecture rules of Constitution Principle V. Do not modify, weaken or disable.
 */
@ArchTag("unit")
@AnalyzeClasses(packages = "com.cocos.challenge", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String DOMAIN = "com.cocos.challenge.domain..";
    private static final String APPLICATION = "com.cocos.challenge.application..";
    private static final String APPLICATION_SERVICE = "com.cocos.challenge.application.service..";
    private static final String PORT_IN_PACKAGE = "com.cocos.challenge.application.port.in";
    private static final String PORT_OUT = "com.cocos.challenge.application.port.out..";
    private static final String REST = "com.cocos.challenge.infrastructure.adapter.in.rest..";
    private static final String PERSISTENCE = "com.cocos.challenge.infrastructure.adapter.out.persistence..";
    private static final String METRICS = "com.cocos.challenge.infrastructure.adapter.out.metrics..";
    private static final String CONFIG = "com.cocos.challenge.infrastructure.config..";

    // Rule 1 ------------------------------------------------------------------------------------------------
    @ArchTest
    static final ArchRule domain_depends_only_on_domain_and_the_jdk = classes()
            .that().resideInAPackage(DOMAIN)
            .should().onlyDependOnClassesThat().resideInAnyPackage(DOMAIN, "java..")
            .because("Constitution V.1: the domain is pure Java (no Spring, Jackson, SQL or Jakarta)")
            .allowEmptyShould(true);

    // Rule 2 ------------------------------------------------------------------------------------------------
    @ArchTest
    static final ArchRule application_depends_only_on_domain_application_jdk_and_transactional = classes()
            .that().resideInAPackage(APPLICATION)
            .should().onlyDependOnClassesThat(
                    resideInAnyPackage(DOMAIN, APPLICATION, "java..")
                            .or(type(Transactional.class))
                            // The default values of @Transactional's members reference these two enums.
                            .or(type(Propagation.class))
                            .or(type(Isolation.class)))
            .because("Constitution V.2: the only Spring dependency allowed in application is @Transactional")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule application_classes_are_not_spring_components = noClasses()
            .that().resideInAPackage(APPLICATION)
            .should().beMetaAnnotatedWith(Component.class)
            .because("Constitution V.2: use cases are registered as beans in infrastructure.config")
            .allowEmptyShould(true);

    // Rule 3 ------------------------------------------------------------------------------------------------
    @ArchTest
    static final ArchRule command_use_case_methods_are_transactional = methods()
            .that().areDeclaredInClassesThat(implementAnInboundPortEndingWith("UseCase"))
            .and().areDeclaredInClassesThat().resideInAPackage(APPLICATION_SERVICE)
            .and().arePublic()
            .and().areNotStatic()
            .and().doNotHaveModifier(JavaModifier.BRIDGE)
            .and().doNotHaveModifier(JavaModifier.SYNTHETIC)
            .should(beAnnotatedWithTransactional(false))
            .because("Constitution V.3 / TEC-14: every use case that changes data runs in one transaction")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule query_use_case_methods_are_read_only_transactional = methods()
            .that().areDeclaredInClassesThat(implementAnInboundPortEndingWith("Query"))
            .and().areDeclaredInClassesThat().resideInAPackage(APPLICATION_SERVICE)
            .and().arePublic()
            .and().areNotStatic()
            .and().doNotHaveModifier(JavaModifier.BRIDGE)
            .and().doNotHaveModifier(JavaModifier.SYNTHETIC)
            .should(beAnnotatedWithTransactional(true))
            .because("Constitution V.3 / TEC-14: queries read one consistent snapshot")
            .allowEmptyShould(true);

    // Rule 4 ------------------------------------------------------------------------------------------------
    @ArchTest
    static final ArchRule only_persistence_adapters_access_the_database = noClasses()
            .that().resideOutsideOfPackages(PERSISTENCE, CONFIG)
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..", "java.sql..", "javax.sql..")
            .because("Constitution V.4 / VI: all SQL lives in the persistence adapters")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule no_orm_anywhere = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(
                    "jakarta.persistence..", "org.hibernate..", "org.springframework.data..", "org.jooq..")
            .because("Constitution VI: explicit SQL with JdbcClient, no ORM")
            .allowEmptyShould(true);

    // Rule 5 ------------------------------------------------------------------------------------------------
    @ArchTest
    static final ArchRule rest_adapters_use_only_inbound_ports = noClasses()
            .that().resideInAPackage(REST)
            .should().dependOnClassesThat().resideInAnyPackage(APPLICATION_SERVICE, PORT_OUT, PERSISTENCE, METRICS)
            .because("Constitution V.5: controllers call inbound ports only")
            .allowEmptyShould(true);

    // Rule 6 ------------------------------------------------------------------------------------------------
    @ArchTest
    static final ArchRule rest_dtos_do_not_leave_the_rest_adapter = noClasses()
            .that().resideOutsideOfPackages(REST, CONFIG)
            .should().dependOnClassesThat().resideInAPackage(REST)
            .because("Constitution V.6: REST DTOs never leave the REST adapter")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule controllers_never_return_domain_objects = methods()
            .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .and().arePublic()
            .should().notHaveRawReturnType(resideInAPackage(DOMAIN))
            .because("Constitution V.6: domain objects are never serialized directly")
            .allowEmptyShould(true);

    // Rule 7 ------------------------------------------------------------------------------------------------
    @ArchTest
    static final ArchRule no_cycles_between_layers = slices()
            .matching("com.cocos.challenge.(*)..")
            .should().beFreeOfCycles()
            .because("Constitution V.7")
            .allowEmptyShould(true);

    // Rule 8 ------------------------------------------------------------------------------------------------
    @ArchTest
    static final ArchRule no_floating_point_fields = fields()
            .that().areDeclaredInClassesThat().resideInAnyPackage(DOMAIN, APPLICATION, REST)
            .should().notHaveRawType(double.class)
            .andShould().notHaveRawType(Double.class)
            .andShould().notHaveRawType(float.class)
            .andShould().notHaveRawType(Float.class)
            .because("Constitution V.8 / VIII: money and percentages are BigDecimal")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule no_floating_point_return_types = methods()
            .that().areDeclaredInClassesThat().resideInAnyPackage(DOMAIN, APPLICATION, REST)
            .should().notHaveRawReturnType(double.class)
            .andShould().notHaveRawReturnType(Double.class)
            .andShould().notHaveRawReturnType(float.class)
            .andShould().notHaveRawReturnType(Float.class)
            .because("Constitution V.8 / VIII: money and percentages are BigDecimal")
            .allowEmptyShould(true);

    // Rule 9 ------------------------------------------------------------------------------------------------
    @ArchTest
    static final ArchRule only_infrastructure_uses_metrics_libraries = noClasses()
            .that().resideInAnyPackage(DOMAIN, APPLICATION)
            .should().dependOnClassesThat().resideInAnyPackage("io.micrometer..", "io.opentelemetry..")
            .because("Constitution V.9 / TEC-31: application records metrics through the BusinessMetrics port")
            .allowEmptyShould(true);

    // Spring Boot 4 guard ---------------------------------------------------------------------------------
    @ArchTest
    static final ArchRule no_jackson_2_databind = noClasses()
            .should().dependOnClassesThat().resideInAPackage("com.fasterxml.jackson.databind..")
            .because("Spring Boot 4 uses Jackson 3 (tools.jackson.*); only com.fasterxml.jackson.annotation remains")
            .allowEmptyShould(true);

    // Helpers -----------------------------------------------------------------------------------------------
    private static DescribedPredicate<JavaClass> implementAnInboundPortEndingWith(String suffix) {
        return DescribedPredicate.describe(
                "implement an interface of " + PORT_IN_PACKAGE + " whose name ends with '" + suffix + "'",
                javaClass -> javaClass.getAllRawInterfaces().stream().anyMatch(port ->
                        port.getPackageName().startsWith(PORT_IN_PACKAGE) && port.getSimpleName().endsWith(suffix)));
    }

    private static ArchCondition<JavaMethod> beAnnotatedWithTransactional(boolean readOnly) {
        String expected = "@Transactional(readOnly = " + readOnly + ")";
        return new ArchCondition<>("be annotated with " + expected) {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean satisfied = method.tryGetAnnotationOfType(Transactional.class)
                        .map(transactional -> transactional.readOnly() == readOnly)
                        .orElse(false);
                String message = method.getFullName() + (satisfied ? " is" : " is not") + " annotated with " + expected;
                events.add(new SimpleConditionEvent(method, satisfied, message));
            }
        };
    }
}
