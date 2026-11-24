package com.cocos.challenge.bdd;

import com.cocos.challenge.bdd.support.TestClockConfiguration;
import com.cocos.challenge.support.PostgresTestcontainersConfiguration;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Spring context of the Cucumber scenarios: the application on a random port, the shared PostgreSQL
 * container and a {@code RestTestClient} (plan.md § Cucumber).
 */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({PostgresTestcontainersConfiguration.class, TestClockConfiguration.class})
@AutoConfigureRestTestClient
public class CucumberSpringConfiguration {
}
