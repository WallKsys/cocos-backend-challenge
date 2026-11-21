# Acceptance scenarios CU-05 (docs/casos-de-uso.md). Spec 007. Copied byte-identical into
# src/test/resources/features/ when spec 007 is implemented. Do not edit (Constitution Principle III).
# User 2 (account 10002) starts with no orders. The idempotency key is a new unique value unless given.

Feature: Deposits
  Users deposit pesos into their own account. A deposit is executed immediately and stored as a FILLED CASH_IN.

  @CU-05.01
  Scenario: A deposit credits the available cash
    When user 2 deposits "100000.00"
    Then the cash movement is created with status "FILLED"
    And the cash movement type is "CASH_IN" and its amount is "100000.00"
    And the stored row of the cash movement has side "CASH_IN", size "100000.00" and price "1.00"
    And the available cash of user 2 is "100000.00"

  @CU-05.02
  Scenario: Deposits accept cents
    When user 2 deposits "1234.56"
    Then the cash movement is created with status "FILLED"
    And the cash movement type is "CASH_IN" and its amount is "1234.56"
    And the available cash of user 2 is "1234.56"

  @CU-05.03
  Scenario: More than two decimals are rejected
    When user 2 sends a deposit with the raw JSON body:
      """
      {"amount": "10.555"}
      """
    Then the response status is 400
    And the error code is "VALIDATION_ERROR"
    And the error detail is "Request validation failed: 'amount' must be a decimal string with up to 16 integer digits and at most 2 decimal places."
    And no order is stored

  @CU-05.04
  Scenario Outline: The amount must be positive
    When user 2 deposits "<amount>"
    Then the response status is 400
    And the error code is "VALIDATION_ERROR"
    And the error detail is "Request validation failed: 'amount' must be greater than 0."
    And no order is stored

    Examples:
      | amount  |
      | 0.00    |
      | -100.00 |

  @CU-05.05
  Scenario: Resending the same deposit does not credit it twice
    Given user 2 deposited "100000.00" with idempotency key "d1"
    When user 2 sends the same cash movement again
    Then the response is a replay of the original cash movement
    And the available cash of user 2 is "100000.00"

  @CU-05.06
  Scenario: A deposit cannot name another user
    When user 2 sends a deposit with the raw JSON body:
      """
      {"amount": "100.00", "userId": 1}
      """
    Then the response status is 400
    And the error code is "VALIDATION_ERROR"
    And the error detail is "Request validation failed: 'userId' is not a recognized field."
    And no order is stored

  @CU-05.07
  Scenario Outline: Reusing a deposit key for a different request is a conflict
    Given user 2 deposited "100000.00" with idempotency key "d1"
    When user 2 <operation> "<amount>" with idempotency key "d1"
    Then the response status is 422
    And the error code is "IDEMPOTENCY_CONFLICT"
    And the error detail is "Idempotency key 'd1' was already used for a different request."
    And the available cash of user 2 is "100000.00"

    Examples:
      | operation | amount    |
      | deposits  | 200000.00 |
      | withdraws | 100000.00 |

  @CU-05.08
  Scenario: The idempotency key is required
    When user 2 deposits "100.00" without idempotency key
    Then the response status is 400
    And the error code is "MISSING_IDEMPOTENCY_KEY"
    And no order is stored
