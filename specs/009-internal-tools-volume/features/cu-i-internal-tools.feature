# Acceptance scenarios CU-I (docs/casos-de-uso.md). Spec 009. Copied byte-identical into
# src/test/resources/features/ when spec 009 is implemented. Do not edit (Constitution Principle III).
#
# INTERNAL TOOLS (level 3): demo-only, NOT part of the challenge. They exist only when internal tools are enabled
# and require the header X-Role: ADMIN. They may break invariants on purpose (DEC-03).
# Seed: order 1 = CASH_IN 1000000.00; 2 = PAMP BUY FILLED; 3 = PAMP BUY CANCELLED; 5 = LOMA BUY LIMIT NEW 50 @ 710;
# 7 = BMA BUY LIMIT NEW 60 @ 1500. LOMA last price 734.65 (prev 734.35); PAMP 925.85 (prev 921.80).

Feature: Internal tools (demo only)
  An administrator prepares and repeats demonstrations: delete orders, force the execution of pending limit
  orders, change prices, restore the seed data and create test users.

  @CU-I.01
  Scenario Outline: Without the administrator role the tools do not exist
    When a user without the administrator role calls "<method>" "<path>"
    Then the response status is 404
    And the error code is "NOT_FOUND"
    And the error detail is "Resource not found."
    And the seed data is unchanged

    Examples:
      | method | path                                       |
      | DELETE | /api/internal/orders/7                     |
      | POST   | /api/internal/orders/7/execute             |
      | PUT    | /api/internal/instruments/47/market-data   |
      | POST   | /api/internal/reset                        |
      | POST   | /api/internal/users                        |

  @CU-I.02
  Scenario: Forcing the execution of a pending limit buy turns the reservation into a position
    When the administrator forces the execution of order 5
    Then the response status is 200
    And the order record has status "FILLED" and price "710.00"
    And the portfolio of user 1 has cash "717500.00" with "90000.00" reserved and "627500.00" available
    And the portfolio of user 1 has the position:
      | ticker | quantity | reserved | last price | market value | average cost | total return | daily change |
      | LOMA   | 50       | 0        | 734.65     | 36732.50     | 710.00       | 3.47         | 0.04         |

  @CU-I.03
  Scenario Outline: Only NEW orders can be executed
    When the administrator forces the execution of order <order>
    Then the response status is 409
    And the error code is "ORDER_NOT_EXECUTABLE"
    And the error detail is "Order <order> cannot be executed because its status is <status>. Only NEW orders can be executed."

    Examples:
      | order | status    |
      | 2     | FILLED    |
      | 3     | CANCELLED |

  @CU-I.04
  Scenario: Deleting the initial deposit leaves a negative cash balance that is shown and flagged
    When the administrator deletes order 1
    Then the response status is 204
    And order 1 does not exist
    And the portfolio of user 1 has cash "-247000.00" with "125500.00" reserved and "-372500.00" available
    And the portfolio of user 1 has total value "-110244.00"
    And the portfolio of user 1 has the warning "NEGATIVE_CASH" with message "Inconsistent account: the cash balance is negative (ARS -247000.00)."

  @CU-I.05
  Scenario: After deleting an order its idempotency key can execute again
    Given user 1 placed an order with idempotency key "k1":
      | instrument | PAMP   |
      | side       | BUY    |
      | type       | MARKET |
      | quantity   | 10     |
    And the administrator deleted that order
    When user 1 sends the same order again
    Then the order is created with status "FILLED"
    And the order id is different from the deleted order
    And user 1 holds 50 shares of "PAMP"

  @CU-I.06
  Scenario: Changing a price changes valuations and search results
    When the administrator sets the market data of "PAMP" to close "1000.00"
    Then the response status is 200
    And the portfolio of user 1 has the position:
      | ticker | quantity | reserved | last price | market value | average cost | total return | daily change |
      | PAMP   | 40       | 0        | 1000.00    | 40000.00     | 930.00       | 7.53         | 8.48         |
    When I search instruments for "pamp"
    Then the instrument "PAMP" has name "Pampa Holding S.A.", type "ACCIONES", last price "1000.00" and daily change "8.48"

  @CU-I.07
  Scenario: Restoring the seed data undoes every change, idempotency keys included
    Given user 1 placed an order with idempotency key "k1":
      | instrument | PAMP   |
      | side       | BUY    |
      | type       | MARKET |
      | quantity   | 10     |
    And the administrator set the market data of "PAMP" to close "1000.00"
    When the administrator restores the seed data
    Then the response status is 204
    And the seed data is unchanged
    And the portfolio of user 1 has total value "889756.00"
    When user 1 places an order with idempotency key "k1":
      | instrument | PAMP   |
      | side       | BUY    |
      | type       | MARKET |
      | quantity   | 10     |
    Then the order is created with status "FILLED"

  @CU-I.08
  Scenario: Creating a test user
    When the administrator creates a user with email "demo@test.com"
    Then the response status is 201
    And the created user has id 5, email "demo@test.com" and account number "10005"
    When user 5 requests their portfolio
    Then the portfolio total value is "0.00"
    And the portfolio has no positions

  @CU-I.09
  Scenario: Forcing the execution of a limit order without market data uses the limit price
    Given user 1 placed an order:
      | instrument | PGR   |
      | side       | BUY   |
      | type       | LIMIT |
      | quantity   | 10    |
      | price      | 50.00 |
    When the administrator forces the execution of that order
    Then the response status is 200
    And the order record has status "FILLED" and price "50.00"
    And the portfolio of user 1 has a position "PGR" with quantity 10 and no last price, market value, total return or daily change
