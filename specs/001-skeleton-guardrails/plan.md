# Implementation Plan: Project skeleton and guardrails

**Branch**: `001-skeleton-guardrails-<slice>` | **Date**: 2026-11-21 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-skeleton-guardrails/spec.md`

## Summary

Create the Maven project (Java 25, Spring Boot 4.1.3), the application entry point, configuration, Docker
image and Docker Compose file, the ArchUnit rules (given **verbatim** below), the test harness (JUnit unit tests,
Testcontainers integration tests, Cucumber BDD) and the traceability check. No business code.

## Technical Context

**Language/Version**: Java 25 (Temurin)

**Primary Dependencies**: Spring Boot 4.1.3 (`spring-boot-starter-webmvc`, `-actuator`, `-jdbc`, `-flyway`,
`-validation`), Flyway PostgreSQL module, PostgreSQL JDBC driver

**Storage**: PostgreSQL 18 (no tables yet; spec 002 adds the migrations)

**Testing**: JUnit + AssertJ (managed by Spring Boot), Testcontainers 2.x (managed by Spring Boot),
Cucumber-JVM 8.1.0, ArchUnit 1.5.1

**Target Platform**: Linux container (Docker), local development on macOS/Linux

**Project Type**: web-service (single Maven module)

**Performance Goals**: N/A in this spec

**Constraints**: Constitution v1.2.0; PR slices of at most 400 counted lines (Principle X)

**Scale/Scope**: skeleton only

## Constitution Check

| Principle | How this spec complies |
|---|---|
| III Test-first | Every slice starts with its tests (see `tasks.md`); skeleton classes only to compile |
| IV Traceability | Implemented here (slice 001-d); scope file starts empty |
| V Hexagonal + ArchUnit | Rules given verbatim in § ArchUnit rules; must pass on the skeleton |
| VI Explicit SQL | No SQL yet; rule "no ORM" active from day one |
| X PR size | Four slices, each estimated under 400 counted lines |

No violations. Complexity tracking: not needed.

## Project Structure

### Documentation (this feature)

```text
specs/001-skeleton-guardrails/
├── spec.md
├── plan.md            # this file
├── research.md        # versions and their sources
├── quickstart.md      # how to verify this spec manually
├── contracts/README.md
└── tasks.md
```

### Source code created by this spec

```text
.                                   (repository root)
├── pom.xml                                     # VERBATIM, § pom.xml
├── mvnw, mvnw.cmd, .mvn/wrapper/               # from Spring Initializr, § Maven Wrapper
├── Dockerfile                                  # VERBATIM, § Docker
├── .dockerignore                               # VERBATIM, § Docker
├── compose.yaml                                # VERBATIM, § Docker
├── .github/workflows/ci.yml                    # VERBATIM, § Continuous integration
├── http/000-health.http                        # § HTTP examples
├── src/main/java/com/cocos/challenge/
│   └── CocosBrokerApiApplication.java
├── src/main/resources/
│   ├── application.yml                         # VERBATIM, § Configuration
│   └── db/migration/.gitkeep                   # empty folder kept for spec 002
├── src/test/java/com/cocos/challenge/
│   ├── support/PostgresTestcontainersConfiguration.java
│   ├── support/IntegrationTest.java            # meta-annotation for integration tests
│   ├── HealthEndpointIT.java
│   ├── HealthDownWhenDatabaseUnavailableIT.java
│   ├── SmokeUnitTest.java
│   ├── architecture/ArchitectureTest.java      # VERBATIM, § ArchUnit rules
│   ├── bdd/CucumberIT.java
│   ├── bdd/CucumberSpringConfiguration.java
│   ├── bdd/steps/HealthSteps.java
│   └── traceability/
│       ├── TraceabilityChecker.java
│       ├── TraceabilityCheckerTest.java
│       └── TraceabilityTest.java
└── src/test/resources/
    ├── junit-platform.properties               # VERBATIM, § Test configuration
    ├── features/000-smoke.feature              # VERBATIM, already written (do not edit)
    └── traceability/scope.txt                  # VERBATIM, § Traceability
```

Delete `src/Main.java`.

**Structure Decision**: single Maven module with the hexagonal packages of Constitution Principle V. Packages
are created when the first class needs them; this spec creates none of `domain`, `application` or
`infrastructure` (ArchUnit rules allow empty packages).

---

## pom.xml

Write **exactly** this file. Do not add, remove or change versions of dependencies.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.3</version>
        <relativePath/>
    </parent>

    <groupId>com.cocos</groupId>
    <artifactId>cocos-broker-api</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>cocos-broker-api</name>
    <description>Broker API: portfolio, instrument search and orders (Cocos backend challenge)</description>

    <properties>
        <java.version>25</java.version>
        <cucumber.version>8.1.0</cucumber.version>
        <archunit.version>1.5.1</archunit.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.cucumber</groupId>
                <artifactId>cucumber-bom</artifactId>
                <version>${cucumber.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <!-- Runtime -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-flyway</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Test: Spring Boot test starters (bring spring-boot-starter-test transitively) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-flyway-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-resttestclient</artifactId>
            <scope>test</scope>
        </dependency>

        <!-- Test: Testcontainers (versions managed by Spring Boot) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-postgresql</artifactId>
            <scope>test</scope>
        </dependency>

        <!-- Test: BDD -->
        <dependency>
            <groupId>io.cucumber</groupId>
            <artifactId>cucumber-java</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.cucumber</groupId>
            <artifactId>cucumber-spring</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.cucumber</groupId>
            <artifactId>cucumber-junit-platform-engine</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.platform</groupId>
            <artifactId>junit-platform-suite</artifactId>
            <scope>test</scope>
        </dependency>

        <!-- Test: architecture rules -->
        <dependency>
            <groupId>com.tngtech.archunit</groupId>
            <artifactId>archunit-junit5</artifactId>
            <version>${archunit.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
            <plugin>
                <!-- Unit tests: *Test classes. Integration tests (*IT) are excluded by default. -->
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
            </plugin>
            <plugin>
                <!-- Integration tests and the Cucumber runner: *IT classes, during `verify`. -->
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-failsafe-plugin</artifactId>
                <executions>
                    <execution>
                        <goals>
                            <goal>integration-test</goal>
                            <goal>verify</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

If a dependency in this file cannot be resolved, or the project does not compile because of a version
incompatibility, **stop and report** (Constitution: Development Workflow). Do not change versions.

## Maven Wrapper

Do not hand-write the wrapper. Download it from Spring Initializr and copy only the wrapper files:

```bash
curl -fsSL "https://start.spring.io/starter.zip?type=maven-project&language=java&bootVersion=4.1.3&javaVersion=25&groupId=com.cocos&artifactId=cocos-broker-api" -o /tmp/initializr.zip
unzip -o /tmp/initializr.zip mvnw mvnw.cmd '.mvn/*' -d .
chmod +x mvnw
```

Commit `mvnw`, `mvnw.cmd` and `.mvn/wrapper/maven-wrapper.properties`.

## Application entry point

`src/main/java/com/cocos/challenge/CocosBrokerApiApplication.java`: a standard `@SpringBootApplication`
class with a `main` method. Nothing else.

## Configuration

`src/main/resources/application.yml` — write exactly:

```yaml
spring:
  application:
    name: cocos-broker-api
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/cocos}
    username: ${DB_USER:cocos}
    password: ${DB_PASSWORD:cocos}
  flyway:
    locations: classpath:db/migration

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: never

logging:
  structured:
    format:
      console: ecs

# Internal (admin/demo) endpoints are disabled by default (TEC-18). Spec 003 reads this property.
cocos:
  internal-tools:
    enabled: false

---
spring:
  config:
    activate:
      on-profile: demo

cocos:
  internal-tools:
    enabled: true
```

## Docker

`Dockerfile` — write exactly:

```dockerfile
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/target/cocos-broker-api-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

`.dockerignore` — write exactly:

```text
target/
.git/
.idea/
docs/
specs/
.specify/
.opencode/
http/
*.md
```

`compose.yaml` — write exactly:

```yaml
services:
  postgres:
    image: postgres:18
    environment:
      POSTGRES_DB: cocos
      POSTGRES_USER: cocos
      POSTGRES_PASSWORD: cocos
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U cocos -d cocos"]
      interval: 2s
      timeout: 3s
      retries: 30

  app:
    build: .
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/cocos
      DB_USER: cocos
      DB_PASSWORD: cocos
      SPRING_PROFILES_ACTIVE: demo
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
```

The database has no volume on purpose: every `docker compose down` + `up` starts from the seed data (spec 002).

## Test configuration

### Conventions (apply to every spec)

| Kind | Class name | JUnit tag | Runs with | Spring / Docker |
|---|---|---|---|---|
| Unit | `*Test` | `@Tag("unit")` | Surefire (`./mvnw test`) | No Spring context, no Docker |
| Integration | `*IT` | `@Tag("integration")` | Failsafe (`./mvnw verify`) | Spring context + PostgreSQL (Testcontainers) |
| BDD | `bdd/CucumberIT` runs all `.feature` files | — | Failsafe | Application on a random port + PostgreSQL |
| Architecture | `architecture/ArchitectureTest` | `@ArchTag("unit")` | Surefire | No |

Scenario IDs are added as extra tags: `@Tag("CU-03.04")`. A test may carry several CU tags.

### Testcontainers

`src/test/java/com/cocos/challenge/support/PostgresTestcontainersConfiguration.java`: a
`@TestConfiguration(proxyBeanMethods = false)` with one `@Bean @ServiceConnection` method returning a
PostgreSQL container for the image `postgres:18`. With Testcontainers 2.x the class is
`org.testcontainers.postgresql.PostgreSQLContainer` (artifact `testcontainers-postgresql`). If that class does
not exist in the resolved artifact, stop and report.

`src/test/java/com/cocos/challenge/support/IntegrationTest.java`: a meta-annotation for integration tests,
combining `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)`,
`@Import(PostgresTestcontainersConfiguration.class)`, `@AutoConfigureRestTestClient` and
`@Tag("integration")`. Every later `*IT` uses it, so all integration tests share one cached Spring context
and one container.

### HTTP calls in tests

Use `RestTestClient` (Spring Framework 7) injected with `@Autowired`, enabled by `@AutoConfigureRestTestClient`.
Do not use `TestRestTemplate` or `MockMvc` for end-to-end tests.

### Cucumber

`src/test/resources/junit-platform.properties` — write exactly:

```properties
cucumber.junit-platform.discovery.as-root-engine=false
cucumber.plugin=pretty, summary
cucumber.publish.quiet=true
```

`bdd/CucumberIT.java`: a JUnit Platform Suite (`@Suite`, `@IncludeEngines("cucumber")`,
`@SelectClasspathResource("features")`, `@ConfigurationParameter(key = GLUE_PROPERTY_NAME,
value = "com.cocos.challenge.bdd")`).

`bdd/CucumberSpringConfiguration.java`: `@CucumberContextConfiguration` plus the same annotations as
`IntegrationTest` (random port, PostgreSQL container, `@AutoConfigureRestTestClient`).

`bdd/steps/HealthSteps.java`: step definitions for `000-smoke.feature`, using `RestTestClient`.

`src/test/resources/features/000-smoke.feature` is **already written**. Do not edit it.

## ArchUnit rules

`src/test/java/com/cocos/challenge/architecture/ArchitectureTest.java` — write **exactly** this file. It is part
of the constitution (Principle V) and is never weakened. If a rule fails to compile against ArchUnit 1.5.1,
stop and report.

```java
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
```

**Naming convention required by rule 3** (applies to all later specs): inbound ports that change data end with
`UseCase` (for example `PlaceOrderUseCase`); inbound ports that only read end with `Query` (for example
`GetPortfolioQuery`). Their implementations live in `application.service`.

## Traceability

### Files

- `src/test/resources/traceability/scope.txt` — write exactly:

  ```text
  # CU groups whose scenarios must be fully traced (Constitution Principle IV).
  # One group per line: CU-01 ... CU-07, CU-I. Each spec appends its own groups.
  # Spec 001 delivers no CU scenarios: the scope is empty.
  ```

- `traceability/TraceabilityChecker.java` — pure Java, no Spring, no file access. Input: the text of
  `docs/casos-de-uso.md`, the contents of each `.feature` file (path → text), the list of test elements
  (identifier → set of JUnit tags), and the scope groups. Output: the list of violations (empty = pass).
- `traceability/TraceabilityTest.java` (`@Tag("unit")`) — reads the real inputs and fails with **all**
  violations in one message:
  - `docs/casos-de-uso.md` (path relative to the project root, which is Maven's working directory);
  - every `src/test/resources/features/**/*.feature`;
  - `src/test/resources/traceability/scope.txt`;
  - test elements: import the compiled test classes of `com.cocos.challenge` with ArchUnit's
    `ClassFileImporter` and `ImportOption.Predefined.ONLY_INCLUDE_TESTS`. A test element is every method
    annotated with `org.junit.jupiter.api.Test`, `ParameterizedTest`, `RepeatedTest` or `TestFactory`. Its tags
    are the union of the `@Tag` values on the method and on its declaring class. **`@Tag` is repeatable**:
    when an element has several, the compiler stores them inside a `@Tags` container annotation; read both.
- `traceability/TraceabilityCheckerTest.java` (`@Tag("unit")`) — tests the checker with in-memory fixtures,
  one test per acceptance scenario of User Story 4.

### Parsing rules

- **Scenario ID**: regular expression `CU-(?:\d{2}|I)\.\d{2}[a-z]?` (matches `CU-03.04`, `CU-02.05b`, `CU-I.01`).
- **IDs in `docs/casos-de-uso.md`**: every line that starts with `| CU-` defines the ID found right after `| `.
- **Gherkin scenarios**: a scenario starts at a line whose trimmed text starts with `Scenario:`,
  `Scenario Outline:` or `Example:`. Its tags are the tag lines (`@...`) immediately above it (ignoring blank
  lines and `#` comments). Tags above the `Feature:` line are feature tags.
- **Scope**: each non-blank, non-comment line of `scope.txt` is a group (`CU-02`, `CU-I`). An ID belongs to group
  `G` when it starts with `G + "."`.

### Violations to report (all of them, not just the first)

1. An ID appears more than once in `docs/casos-de-uso.md`.
2. A `@CU-...` tag in a feature file is not an ID of `docs/casos-de-uso.md`.
3. A `@CU-...` tag is placed at feature level (CU tags belong to scenarios only).
4. A scenario has more than one `@CU-...` tag.
5. A scenario has no `@CU-...` tag and is not tagged `@smoke`.
6. An ID appears in more than one scenario.
7. For an ID in scope: no scenario is tagged with it.
8. For an ID in scope: no test element is tagged with it.
9. For an ID in scope: none of its test elements is also tagged `unit` or `integration`.
10. A test element is tagged with a CU ID that is not an ID of `docs/casos-de-uso.md`.

Each violation message names the ID (or file and scenario) and the rule, for example
`CU-02.01: in scope but no JUnit test is tagged "CU-02.01" (rule 8)`.

## Continuous integration

`.github/workflows/ci.yml` — write exactly. It runs the full build (`./mvnw verify`: unit, integration with
Testcontainers, BDD, ArchUnit, traceability) on every pull request and on every push to `main`. GitHub-hosted Ubuntu
runners include Docker, so Testcontainers works without extra setup. The volume profile never runs here.

```yaml
name: CI

on:
  pull_request:
  push:
    branches: [main]

permissions:
  contents: read

jobs:
  verify:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v7

      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: '25'
          cache: maven

      - name: Build and test (unit, integration, BDD, ArchUnit, traceability)
        run: ./mvnw -B verify
```

The job is named `verify`; that is the name of the status check. After slice 001-a is merged, the repository owner
adds `verify` to the required status checks of `main` (next to `pr-size`), so that no PR with failing tests can be
merged. It cannot be required earlier: until the workflow exists, the check would never report and every PR would be
blocked.

## HTTP examples

`http/000-health.http` (IntelliJ HTTP client):

```http
### Application health
GET http://localhost:8080/actuator/health
```

## Verification of this spec

See [quickstart.md](quickstart.md).
