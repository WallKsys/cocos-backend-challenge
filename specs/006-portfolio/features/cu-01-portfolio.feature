# Acceptance scenarios CU-01 (docs/casos-de-uso.md). Spec 006. Copied byte-identical into
# src/test/resources/features/ when spec 006 is implemented. Do not edit (Constitution Principle III).
#
# Seed state of user 1: cash 753000.00, reserved 125500.00 (LIMIT buys of LOMA and BMA), available 627500.00;
# PAMP 40 (bought 50 at 930, sold 10), METR 500 (bought at 250), BMA -10 (bought 20 at 1540, sold 30).
# Last prices (2023-07-14): PAMP 925.85 (prev 921.80), METR 229.50 (prev 232.00), BMA 1502.80 (prev 1520.25).
# In position tables, "-" means null.

Feature: Portfolio
  A user sees the total value of the account, the cash available to trade and every position held,
  with its market value, average cost, total return and daily change.

  @CU-01.01
  Scenario: Portfolio of the seed user
    When user 1 requests their portfolio
    Then the portfolio total value is "889756.00"
    And the portfolio cash is "753000.00" with "125500.00" reserved and "627500.00" available
    And the portfolio positions are:
      | ticker | quantity | reserved | last price | market value | average cost | total return | daily change |
      | BMA    | -10      | 0        | 1502.80    | -15028.00    | 1540.00      | -2.42        | -1.15        |
      | METR   | 500      | 0        | 229.50     | 114750.00    | 250.00       | -8.20        | -1.08        |
      | PAMP   | 40       | 0        | 925.85     | 37034.00     | 930.00       | -0.45        | 0.44         |

  @CU-01.02
  Scenario: Total return against the average cost and daily change against the previous close
    When user 1 requests their portfolio
    Then the position "PAMP" has average cost "930.00", total return "-0.45" and daily change "0.44"

  @CU-01.03
  Scenario: The inconsistent negative seed position is shown and flagged
    When user 1 requests their portfolio
    Then the position "BMA" has quantity -10 and market value "-15028.00"
    And the position "BMA" has the warning "NEGATIVE_POSITION" with message "Inconsistent position: the quantity of BMA is negative (-10); the recorded orders sell more shares than were bought."
    And the portfolio total value is "889756.00"

  @CU-01.04
  Scenario: A pending limit buy reserves cash but is not a position
    When user 1 requests their portfolio
    Then the portfolio has no position "LOMA"
    And the portfolio cash is "753000.00" with "125500.00" reserved and "627500.00" available

  @CU-01.05
  Scenario: A position sold completely disappears
    Given user 1 placed an order:
      | instrument | PAMP   |
      | side       | SELL   |
      | type       | MARKET |
      | quantity   | 40     |
    When user 1 requests their portfolio
    Then the portfolio has no position "PAMP"

  @CU-01.06
  Scenario: A position without market data has no value and is excluded from the total
    Given user 1 has these filled orders:
      | instrument | side | quantity | price | datetime             |
      | PGR        | BUY  | 10       | 50.00 | 2023-07-14T12:00:00Z |
    When user 1 requests their portfolio
    Then the position "PGR" has quantity 10 and no last price, market value, total return or daily change
    And the position "PGR" has the warning "MISSING_MARKET_DATA" with message "No market data is available for PGR; its price, value and returns are unknown."
    And the portfolio has the warning "TOTAL_EXCLUDES_UNPRICED" with message "The total value excludes positions without market data: PGR."
    And the portfolio total value is "889256.00"

  @CU-01.07
  Scenario: A user without orders has an empty portfolio
    When user 2 requests their portfolio
    Then the response status is 200
    And the portfolio total value is "0.00"
    And the portfolio cash is "0.00" with "0.00" reserved and "0.00" available
    And the portfolio has no positions
    And the portfolio has no warnings

  @CU-01.08
  Scenario: An unknown user has no portfolio
    When user 999 requests their portfolio
    Then the response status is 404
    And the error code is "NOT_FOUND"
    And the error detail is "User 999 was not found."

  @CU-01.09
  Scenario: A user can only see their own portfolio
    When user 2 requests their portfolio
    Then the portfolio has no positions
    When user 2 requests the URL "/api/v1/users/1/portfolio"
    Then the response status is 404
    And the error code is "NOT_FOUND"

  @CU-01.10
  Scenario: The administrator can see the portfolio of any user
    When the administrator requests the portfolio of user 1
    Then the response status is 200
    And the portfolio total value is "889756.00"
    And the portfolio positions are:
      | ticker | quantity | reserved | last price | market value | average cost | total return | daily change |
      | BMA    | -10      | 0        | 1502.80    | -15028.00    | 1540.00      | -2.42        | -1.15        |
      | METR   | 500      | 0        | 229.50     | 114750.00    | 250.00       | -8.20        | -1.08        |
      | PAMP   | 40       | 0        | 925.85     | 37034.00     | 930.00       | -0.45        | 0.44         |

  @CU-01.11
  Scenario: An executed order is reflected immediately
    Given user 1 placed an order:
      | instrument | PAMP   |
      | side       | BUY    |
      | type       | MARKET |
      | quantity   | 10     |
    When user 1 requests their portfolio
    Then the position "PAMP" has quantity 50 and market value "46292.50"
    And the portfolio cash is "743741.50" with "125500.00" reserved and "618241.50" available

  @CU-01.12
  Scenario: A sale does not change the average cost and a new buy averages against the shares still held
    Given user 1 placed an order:
      | instrument | PAMP   |
      | side       | BUY    |
      | type       | MARKET |
      | quantity   | 10     |
    When user 1 requests their portfolio
    Then the position "PAMP" has average cost "929.17", total return "-0.36" and daily change "0.44"

  @CU-01.13
  Scenario: After a position reaches zero the average cost starts again
    Given user 1 placed an order:
      | instrument | PAMP   |
      | side       | SELL   |
      | type       | MARKET |
      | quantity   | 40     |
    And the last price of "PAMP" is "1000.00"
    And user 1 placed an order:
      | instrument | PAMP   |
      | side       | BUY    |
      | type       | MARKET |
      | quantity   | 10     |
    When user 1 requests their portfolio
    Then the position "PAMP" has average cost "1000.00", total return "0.00" and daily change "8.48"

  @CU-01.14
  Scenario: Orders are processed by execution time, not by insertion order
    Given user 2 has these filled orders:
      | instrument | side    | quantity | price  | datetime             |
      | ARS        | CASH_IN | 100000   | 1.00   | 2023-07-12T10:00:00Z |
      | PAMP       | BUY     | 5        | 200.00 | 2023-07-12T13:00:00Z |
      | PAMP       | BUY     | 10       | 100.00 | 2023-07-12T11:00:00Z |
      | PAMP       | SELL    | 10       | 120.00 | 2023-07-12T12:00:00Z |
    When user 2 requests their portfolio
    Then the position "PAMP" has quantity 5 and market value "4629.25"
    And the position "PAMP" has average cost "200.00", total return "362.93" and daily change "0.44"
