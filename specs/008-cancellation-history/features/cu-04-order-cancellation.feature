# Acceptance scenarios CU-04 (docs/casos-de-uso.md). Spec 008. Copied byte-identical into
# src/test/resources/features/ when spec 008 is implemented. Do not edit (Constitution Principle III).
#
# Seed orders of user 1 used here: 7 = BMA BUY LIMIT NEW 60 @ 1500 (reserves 90000.00); 2 = PAMP BUY FILLED;
# 6 = PAMP SELL REJECTED. Seed available cash of user 1: 627500.00.

Feature: Order cancellation
  Users cancel their own NEW orders, which releases what the order reserved.
  Cancelling an already cancelled order succeeds without side effects; other statuses cannot be cancelled.

  @CU-04.01
  Scenario: Cancelling a pending limit buy releases the reserved cash
    When user 1 cancels order 7
    Then the response status is 200
    And the cancelled order has status "CANCELLED" and was not already cancelled
    And the available cash of user 1 is "717500.00"

  @CU-04.02
  Scenario: Cancelling a pending limit sell releases the reserved shares
    Given user 1 placed an order:
      | instrument | PAMP   |
      | side       | SELL   |
      | type       | LIMIT  |
      | quantity   | 30     |
      | price      | 950.00 |
    When user 1 cancels that order
    Then the response status is 200
    And the cancelled order has status "CANCELLED" and was not already cancelled
    And user 1 can sell 40 shares of "PAMP"

  @CU-04.03
  Scenario: Cancelling twice succeeds and releases the reservation only once
    Given user 1 cancelled order 7
    When user 1 cancels order 7
    Then the response status is 200
    And the cancelled order has status "CANCELLED" and was already cancelled
    And the available cash of user 1 is "717500.00"

  @CU-04.04
  Scenario: A filled order cannot be cancelled
    When user 1 cancels order 2
    Then the response status is 409
    And the error code is "ORDER_NOT_CANCELLABLE"
    And the error detail is "Order 2 cannot be cancelled because its status is FILLED. Only NEW orders can be cancelled."
    And order 2 has status "FILLED"

  @CU-04.05
  Scenario: A rejected order cannot be cancelled
    When user 1 cancels order 6
    Then the response status is 409
    And the error code is "ORDER_NOT_CANCELLABLE"
    And the error detail is "Order 6 cannot be cancelled because its status is REJECTED. Only NEW orders can be cancelled."

  @CU-04.06
  Scenario: A user cannot cancel another user's order
    When user 2 cancels order 7
    Then the response status is 404
    And the error code is "NOT_FOUND"
    And the error detail is "Order 7 was not found."
    And order 7 has status "NEW"
    And the available cash of user 1 is "627500.00"

  @CU-04.07
  Scenario: An unknown order cannot be cancelled
    When user 1 cancels order 9999
    Then the response status is 404
    And the error code is "NOT_FOUND"
    And the error detail is "Order 9999 was not found."

  @CU-04.08
  Scenario: A cancellation and a forced execution at the same time never both apply
    When user 1 cancels order 7 while the administrator forces its execution at the same time
    Then exactly one of the two operations is applied to order 7
    And the cancellation response is consistent with the final status of order 7
