package com.cocos.challenge.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-annotation for integration tests: the application on a random port, the shared PostgreSQL
 * container and a {@code RestTestClient} (plan.md § Testcontainers, § HTTP calls in tests).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestcontainersConfiguration.class)
@AutoConfigureRestTestClient
@Tag("integration")
public @interface IntegrationTest {
}
