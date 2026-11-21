# Acceptance scenarios CU-06 (docs/casos-de-uso.md). Spec 007. Copied byte-identical into
# src/test/resources/features/ when spec 007 is implemented. Do not edit (Constitution Principle III).
# Seed state of user 1 (account 10001): cash 753000.00, reserved 125500.00, available 627500.00.

Feature: Withdrawals
  Users withdraw pesos from their available cash. Reserved cash cannot be withdrawn.
  A withdrawal above the available cash is stored as REJECTED; an invalid request stores nothing.

  @CU-06.01
  Scenario: A withdrawal debits the available cash
    When user 1 withdraws "100000.00"
    Then the cash movement is created with status "FILLED"
    And the cash movement type is "CASH_OUT" and its amount is "100000.00"
    And the available cash of user 1 is "527500.00"

  @CU-06.02
  Scenario: Reserved cash cannot be withdrawn
    When user 1 withdraws "700000.00"
    Then the cash movement is created with status "REJECTED"
    And the cash movement rejection is "INSUFFICIENT_FUNDS" with message "Insufficient available cash in account 10001: required ARS 700000.00, available ARS 627500.00."
    And the available cash of user 1 is "627500.00"

  @CU-06.03
  Scenario: The whole available cash can be withdrawn, cents included
    Given user 1 placed an order:
      | instrument | PAMP   |
      | side       | BUY    |
      | type       | MARKET |
      | quantity   | 10     |
    When user 1 withdraws "618241.50"
    Then the cash movement is created with status "FILLED"
    And the available cash of user 1 is "0.00"

  @CU-06.04
  Scenario: Resending the same withdrawal does not debit it twice
    Given user 1 withdrew "100000.00" with idempotency key "r1"
    When user 1 sends the same cash movement again
    Then the response is a replay of the original cash movement
    And the available cash of user 1 is "527500.00"

  @CU-06.05
  Scenario: A withdrawal cannot name another user
    When user 2 sends a withdrawal with the raw JSON body:
      """
      {"amount": "100.00", "userId": 1}
      """
    Then the response status is 400
    And the error code is "VALIDATION_ERROR"
    And the error detail is "Request validation failed: 'userId' is not a recognized field."
    And no order is stored

  @CU-06.06
  Scenario: Reusing a withdrawal key with a different amount is a conflict
    Given user 1 withdrew "100000.00" with idempotency key "r1"
    When user 1 withdraws "50000.00" with idempotency key "r1"
    Then the response status is 422
    And the error code is "IDEMPOTENCY_CONFLICT"
    And the available cash of user 1 is "527500.00"

  @CU-06.07
  Scenario Outline: An invalid amount stores nothing, unlike a withdrawal without funds
    When user 1 withdraws "<amount>"
    Then the response status is 400
    And the error code is "VALIDATION_ERROR"
    And the error detail is "Request validation failed: 'amount' must be greater than 0."
    And no order is stored

    Examples:
      | amount  |
      | 0.00    |
      | -100.00 |

  @CU-06.08
  Scenario: More than two decimals are rejected
    When user 1 sends a withdrawal with the raw JSON body:
      """
      {"amount": "10.555"}
      """
    Then the response status is 400
    And the error code is "VALIDATION_ERROR"
    And the error detail is "Request validation failed: 'amount' must be a decimal string with up to 16 integer digits and at most 2 decimal places."
    And no order is stored

  @CU-06.09
  Scenario: The idempotency key is required
    When user 1 withdraws "100.00" without idempotency key
    Then the response status is 400
    And the error code is "MISSING_IDEMPOTENCY_KEY"
    And no order is stored
