# Acceptance scenarios CU-07 (docs/casos-de-uso.md). Spec 008. Copied byte-identical into
# src/test/resources/features/ when spec 008 is implemented. Do not edit (Constitution Principle III).
#
# Seed orders of user 1, newest first (id ticker side status, UTC):
# 11 BMA SELL FILLED 2023-07-13T15:13:20Z · 10 METR BUY FILLED · 9 BMA BUY FILLED · 8 ARS CASH_OUT FILLED ·
# 7 BMA BUY NEW · 6 PAMP SELL REJECTED · 5 LOMA BUY NEW · 4 PAMP SELL FILLED · 3 PAMP BUY CANCELLED ·
# 2 PAMP BUY FILLED · 1 ARS CASH_IN FILLED 2023-07-12T12:11:20Z

Feature: Order history
  Users list their own orders and cash movements, newest first, with cursor pagination and an optional
  status filter, and read a single order. The administrator can list the orders of any user.

  @CU-07.01
  Scenario: The history lists every order of the user, newest first, in UTC
    When user 1 requests their order history
    Then the history order ids are "11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1"
    And the history entry of order 11 has ticker "BMA", side "SELL", status "FILLED" and timestamp "2023-07-13T15:13:20Z"
    And the history entry of order 1 has ticker "ARS", side "CASH_IN", status "FILLED" and timestamp "2023-07-12T12:11:20Z"
    And there is no next history page

  @CU-07.02
  Scenario: Filtering by status
    When user 1 requests their order history with status "NEW"
    Then the history order ids are "7, 5"

  @CU-07.03
  Scenario: A rejected order shows its reason
    Given user 1 placed an order:
      | instrument | PAMP   |
      | side       | BUY    |
      | type       | MARKET |
      | quantity   | 1000   |
    When user 1 requests that order
    Then the response status is 200
    And the order record has status "REJECTED" and rejection "INSUFFICIENT_FUNDS" with message "Insufficient available cash in account 10001: required ARS 925850.00, available ARS 627500.00."

  @CU-07.04
  Scenario: A rejected seed order has no recorded reason
    When user 1 requests order 6
    Then the response status is 200
    And the order record has status "REJECTED" and no rejection

  @CU-07.05
  Scenario: A user cannot read another user's order
    When user 2 requests order 7
    Then the response status is 404
    And the error code is "NOT_FOUND"
    And the error detail is "Order 7 was not found."

  @CU-07.06
  Scenario: The administrator can list the orders of any user
    When the administrator requests the order history of user 1
    Then the response status is 200
    And the history order ids are "11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1"

  @CU-07.07
  Scenario: Paging through the history returns every order once
    When user 1 pages through their order history with a page size of 5
    Then the history pages contain the order ids "11, 10, 9, 8, 7", "6, 5, 4, 3, 2" and "1"

  @CU-07.08
  Scenario: Filtering by cancelled orders
    When user 1 requests their order history with status "CANCELLED"
    Then the history order ids are "3"

  @CU-07.09
  Scenario: A filter without matches returns an empty page
    When user 2 requests their order history with status "CANCELLED"
    Then the response status is 200
    And the history is empty
    And there is no next history page
