# Smoke test of the BDD harness (spec 001). Not an acceptance scenario: it carries no CU tag.
# Do not edit (Constitution Principle III).

@smoke
Feature: Application health
  The application reports whether it is able to serve requests.

  Scenario: The application reports that it is up
    When I request the application health
    Then the response status is 200
    And the reported health status is "UP"
