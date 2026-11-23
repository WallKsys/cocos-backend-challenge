# Acceptance scenarios CU-03 (docs/casos-de-uso.md). Spec 005. Copied byte-identical into
# src/test/resources/features/ when spec 005 is implemented. Do not edit (Constitution Principle III).
#
# Seed state of user 1 (account 10001): cash 753000.00, reserved 125500.00, available 627500.00;
# PAMP 40 shares (last price 925.85), METR 500, BMA -10; PGR has no market data. User 2 (account 10002) has nothing.
# Order fields not listed in a table are absent; the idempotency key is a new unique value unless given.

Feature: Order placement
  Users send BUY and SELL orders, MARKET or LIMIT, by quantity or by amount.
  MARKET orders execute at the last price; LIMIT orders stay NEW and reserve what they commit.
  Orders that the business cannot fulfil are stored as REJECTED with a reason; invalid requests store nothing.

  # ---------------------------------------------------------------- MARKET orders

  @CU-03.01
  Scenario: Buy at market by quantity
    When user 1 places an order:
      | instrument | PAMP   |
      | side       | BUY    |
      | type       | MARKET |
      | quantity   | 10     |
    Then the order is created with status "FILLED"
    And the order quantity is 10
    And the order price is "925.85"
    And the order total amount is "9258.50"
    And the available cash of user 1 is "618241.50"
    And user 1 holds 50 shares of "PAMP"

  @CU-03.02
  Scenario: Buy at market by amount uses whole shares and keeps the remainder available
    When user 1 places an order:
      | instrument | PAMP     |
      | side       | BUY      |
      | type       | MARKET   |
      | amount     | 50000.00 |
    Then the order is created with status "FILLED"
    And the order quantity is 54
    And the order total amount is "49995.90"
    And the order requested amount is "50000.00" and the remaining amount is "4.10"
    And the available cash of user 1 is "577504.10"

  @CU-03.03
  Scenario: A buy above the available cash is rejected and stored
    When user 1 places an order:
      | instrument | PAMP   |
      | side       | BUY    |
      | type       | MARKET |
      | quantity   | 1000   |
    Then the order is created with status "REJECTED"
    And the order rejection is "INSUFFICIENT_FUNDS" with message "Insufficient available cash in account 10001: required ARS 925850.00, available ARS 627500.00."
    And the available cash of user 1 is "627500.00"
    And user 1 holds 40 shares of "PAMP"

  @CU-03.04
  Scenario: Reserved cash cannot be spent
    When user 1 places an order:
      | instrument | PAMP   |
      | side       | BUY    |
      | type       | MARKET |
      | quantity   | 700    |
    Then the order is created with status "REJECTED"
    And the order rejection is "INSUFFICIENT_FUNDS" with message "Insufficient available cash in account 10001: required ARS 648095.00, available ARS 627500.00."

  @CU-03.05
  Scenario: Sell at market by quantity
    When user 1 places an order:
      | instrument | PAMP   |
      | side       | SELL   |
      | type       | MARKET |
      | quantity   | 10     |
    Then the order is created with status "FILLED"
    And the order total amount is "9258.50"
    And the available cash of user 1 is "636758.50"
    And user 1 holds 30 shares of "PAMP"

  @CU-03.06
  Scenario: A sell above the available shares is rejected
    When user 1 places an order:
      | instrument | PAMP   |
      | side       | SELL   |
      | type       | MARKET |
      | quantity   | 41     |
    Then the order is created with status "REJECTED"
    And the order rejection is "INSUFFICIENT_SHARES" with message "Insufficient available shares of PAMP in account 10001: required 41, available 40."
    And user 1 holds 40 shares of "PAMP"

  @CU-03.07
  Scenario: Selling an instrument that is not held is rejected
    When user 1 places an order:
      | instrument | GGAL   |
      | side       | SELL   |
      | type       | MARKET |
      | quantity   | 1      |
    Then the order is created with status "REJECTED"
    And the order rejection is "INSUFFICIENT_SHARES" with message "Insufficient available shares of GGAL in account 10001: required 1, available 0."

  @CU-03.08
  Scenario: Selling from the inconsistent negative seed position is rejected
    When user 1 places an order:
      | instrument | BMA    |
      | side       | SELL   |
      | type       | MARKET |
      | quantity   | 1      |
    Then the order is created with status "REJECTED"
    And the order rejection is "INSUFFICIENT_SHARES" with message "Insufficient available shares of BMA in account 10001: required 1, available -10."

  @CU-03.09
  Scenario: A sell by amount is never reduced to the available shares
    When user 1 places an order:
      | instrument | PAMP     |
      | side       | SELL     |
      | type       | MARKET   |
      | amount     | 50000.00 |
    Then the order is created with status "REJECTED"
    And the order quantity is 54
    And the order rejection is "INSUFFICIENT_SHARES" with message "Insufficient available shares of PAMP in account 10001: required 54, available 40."

  @CU-03.10
  Scenario: Sell at market by amount
    When user 1 places an order:
      | instrument | PAMP     |
      | side       | SELL     |
      | type       | MARKET   |
      | amount     | 20000.00 |
    Then the order is created with status "FILLED"
    And the order quantity is 21
    And the order total amount is "19442.85"
    And the order requested amount is "20000.00" and the remaining amount is "557.15"
    And the available cash of user 1 is "646942.85"
    And user 1 holds 19 shares of "PAMP"

  @CU-03.11
  Scenario: A buy amount below the price of one share is rejected
    When user 1 places an order:
      | instrument | PAMP   |
      | side       | BUY    |
      | type       | MARKET |
      | amount     | 500.00 |
    Then the order is created with status "REJECTED"
    And the order quantity is 0
    And the order rejection is "AMOUNT_BELOW_PRICE" with message "The amount ARS 500.00 is not enough to buy one share of PAMP at ARS 925.85."

  @CU-03.12
  Scenario: A market order without market data is rejected
    When user 1 places an order:
      | instrument | PGR    |
      | side       | BUY    |
      | type       | MARKET |
      | quantity   | 1      |
    Then the order is created with status "REJECTED"
    And the order has no price
    And the order rejection is "NO_MARKET_PRICE" with message "No market price is available for PGR; MARKET orders cannot be executed."

  @CU-03.39
  Scenario: A sell amount below the price of one share is rejected
    When user 1 places an order:
      | instrument | PAMP   |
      | side       | SELL   |
      | type       | MARKET |
      | amount     | 500.00 |
    Then the order is created with status "REJECTED"
    And the order rejection is "AMOUNT_BELOW_PRICE" with message "The amount ARS 500.00 is not enough to sell one share of PAMP at ARS 925.85."

  # ---------------------------------------------------------------- LIMIT orders

  @CU-03.13
  Scenario: A limit buy stays NEW and reserves cash
    When user 1 places an order:
      | instrument | PAMP   |
      | side       | BUY    |
      | type       | LIMIT  |
      | quantity   | 100    |
      | price      | 900.00 |
    Then the order is created with status "NEW"
    And the order total amount is "90000.00"
    And the available cash of user 1 is "537500.00"
    And user 1 holds 40 shares of "PAMP"

  @CU-03.14
  Scenario: A limit buy by amount reserves only the amount actually used
    When user 1 places an order:
      | instrument | PAMP     |
      | side       | BUY      |
      | type       | LIMIT    |
      | amount     | 50000.00 |
      | price      | 925.85   |
    Then the order is created with status "NEW"
    And the order quantity is 54
    And the order total amount is "49995.90"
    And the available cash of user 1 is "577504.10"

  @CU-03.15
  Scenario: A limit sell stays NEW and reserves shares
    When user 1 places an order:
      | instrument | PAMP   |
      | side       | SELL   |
      | type       | LIMIT  |
      | quantity   | 30     |
      | price      | 950.00 |
    Then the order is created with status "NEW"
    And user 1 holds 40 shares of "PAMP"
    And user 1 can sell 10 shares of "PAMP"

  @CU-03.16
  Scenario: Reserved shares cannot be sold
    Given user 1 placed an order:
      | instrument | PAMP   |
      | side       | SELL   |
      | type       | LIMIT  |
      | quantity   | 30     |
      | price      | 950.00 |
    When user 1 places an order:
      | instrument | PAMP   |
      | side       | SELL   |
      | type       | MARKET |
      | quantity   | 11     |
    Then the order is created with status "REJECTED"
    And the order rejection is "INSUFFICIENT_SHARES" with message "Insufficient available shares of PAMP in account 10001: required 11, available 10."

  @CU-03.17
  Scenario: A limit order better than the market is not executed immediately
    When user 1 places an order:
      | instrument | PAMP    |
      | side       | BUY     |
      | type       | LIMIT   |
      | quantity   | 10      |
      | price      | 1000.00 |
    Then the order is created with status "NEW"
    And the available cash of user 1 is "617500.00"

  @CU-03.18
  Scenario: A limit buy above the available cash is rejected
    When user 1 places an order:
      | instrument | PAMP   |
      | side       | BUY    |
      | type       | LIMIT  |
      | quantity   | 1000   |
      | price      | 900.00 |
    Then the order is created with status "REJECTED"
    And the order rejection is "INSUFFICIENT_FUNDS" with message "Insufficient available cash in account 10001: required ARS 900000.00, available ARS 627500.00."

  @CU-03.19
  Scenario: A limit order does not need market data
    When user 1 places an order:
      | instrument | PGR   |
      | side       | BUY   |
      | type       | LIMIT |
      | quantity   | 10    |
      | price      | 50.00 |
    Then the order is created with status "NEW"
    And the order total amount is "500.00"

  @CU-03.38
  Scenario: A limit sell by amount reserves whole shares
    When user 1 places an order:
      | instrument | PAMP     |
      | side       | SELL     |
      | type       | LIMIT    |
      | amount     | 20000.00 |
      | price      | 950.00   |
    Then the order is created with status "NEW"
    And the order quantity is 21
    And the order total amount is "19950.00"
    And the order requested amount is "20000.00" and the remaining amount is "50.00"
    And user 1 can sell 19 shares of "PAMP"

  @CU-03.40
  Scenario: The quantity of a limit order by amount uses the limit price
    When user 1 places an order:
      | instrument | PAMP    |
      | side       | BUY     |
      | type       | LIMIT   |
      | amount     | 800.00  |
      | price      | 1000.00 |
    Then the order is created with status "REJECTED"
    And the order rejection is "AMOUNT_BELOW_PRICE" with message "The amount ARS 800.00 is not enough to buy one share of PAMP at ARS 1000.00."

  @CU-03.43
  Scenario: A limit price above the price band is rejected
    When user 1 places an order:
      | instrument | PAMP    |
      | side       | BUY     |
      | type       | LIMIT   |
      | quantity   | 10      |
      | price      | 1111.03 |
    Then the order is created with status "REJECTED"
    And the order rejection is "PRICE_OUT_OF_RANGE" with message "The limit price ARS 1111.03 is outside the allowed range for PAMP: ARS 740.68 to ARS 1111.02 (20% around the last price ARS 925.85)."
    And the available cash of user 1 is "627500.00"

  @CU-03.44
  Scenario: A limit price below the price band is rejected
    When user 1 places an order:
      | instrument | PAMP   |
      | side       | SELL   |
      | type       | LIMIT  |
      | quantity   | 10     |
      | price      | 740.67 |
    Then the order is created with status "REJECTED"
    And the order rejection is "PRICE_OUT_OF_RANGE" with message "The limit price ARS 740.67 is outside the allowed range for PAMP: ARS 740.68 to ARS 1111.02 (20% around the last price ARS 925.85)."
    And user 1 can sell 40 shares of "PAMP"

  @CU-03.45
  Scenario: A limit price on the edge of the price band is accepted
    When user 1 places an order:
      | instrument | PAMP    |
      | side       | BUY     |
      | type       | LIMIT   |
      | quantity   | 10      |
      | price      | 1111.02 |
    Then the order is created with status "NEW"
    And the available cash of user 1 is "616389.80"

  @CU-03.46
  Scenario: An order by amount that would buy too many shares is rejected
    When user 1 places an order:
      | instrument | PGR           |
      | side       | BUY           |
      | type       | LIMIT         |
      | amount     | 9999999999.00 |
      | price      | 0.01          |
    Then the order is created with status "REJECTED"
    And the order rejection is "QUANTITY_TOO_LARGE" with message "The amount ARS 9999999999.00 would buy more than 2147483647 shares of PGR at ARS 0.01."

  # ---------------------------------------------------------------- Invalid requests: nothing is stored

  @CU-03.20
  Scenario: An unknown user cannot place orders
    When user 999 places an order:
      | instrument | PAMP   |
      | side       | BUY    |
      | type       | MARKET |
      | quantity   | 1      |
    Then the response status is 404
    And the error code is "NOT_FOUND"
    And the error detail is "User 999 was not found."
    And no order is stored

  @CU-03.21
  Scenario: An unknown instrument is rejected as invalid
    When user 1 places an order:
      | instrumentId | 999    |
      | side         | BUY    |
      | type         | MARKET |
      | quantity     | 1      |
    Then the response status is 400
    And the error code is "VALIDATION_ERROR"
    And the error detail is "Request validation failed: 'instrumentId' does not reference an existing instrument."
    And no order is stored

  @CU-03.22
  Scenario Outline: Quantity and amount must be positive
    When user 1 places an order:
      | instrument | PAMP     |
      | side       | BUY      |
      | type       | MARKET   |
      | <field>    | <value>  |
    Then the response status is 400
    And the error code is "VALIDATION_ERROR"
    And the error detail is "<detail>"
    And no order is stored

    Examples:
      | field    | value | detail                                                   |
      | quantity | 0     | Request validation failed: 'quantity' must be greater than 0. |
      | quantity | -5    | Request validation failed: 'quantity' must be greater than 0. |
      | amount   | 0.00  | Request validation failed: 'amount' must be greater than 0.   |

  @CU-03.23
  Scenario: Fractional shares are rejected
    When user 1 sends an order with the raw JSON body:
      """
      {"instrumentId": 47, "side": "BUY", "type": "MARKET", "quantity": 1.5}
      """
    Then the response status is 400
    And the error code is "VALIDATION_ERROR"
    And the error detail is "Request validation failed: 'quantity' has an invalid type or format."
    And no order is stored

  @CU-03.24
  Scenario Outline: A limit order needs a positive price
    When user 1 sends an order with the raw JSON body:
      """
      <body>
      """
    Then the response status is 400
    And the error code is "VALIDATION_ERROR"
    And the error detail is "<detail>"
    And no order is stored

    Examples:
      | body                                                                                   | detail                                                           |
      | {"instrumentId": 47, "side": "BUY", "type": "LIMIT", "quantity": 1}                    | Request validation failed: 'price' is required for LIMIT orders. |
      | {"instrumentId": 47, "side": "BUY", "type": "LIMIT", "quantity": 1, "price": "0.00"}   | Request validation failed: 'price' must be greater than 0.       |

  @CU-03.25
  Scenario: A market order must not carry a price
    When user 1 places an order:
      | instrument | PAMP   |
      | side       | BUY    |
      | type       | MARKET |
      | quantity   | 1      |
      | price      | 900.00 |
    Then the response status is 400
    And the error code is "VALIDATION_ERROR"
    And the error detail is "Request validation failed: 'price' must not be sent for MARKET orders."
    And no order is stored

  @CU-03.26
  Scenario Outline: Exactly one of quantity and amount is required
    When user 1 sends an order with the raw JSON body:
      """
      <body>
      """
    Then the response status is 400
    And the error code is "VALIDATION_ERROR"
    And the error detail is "Request validation failed: exactly one of 'quantity' or 'amount' is required."
    And no order is stored

    Examples:
      | body                                                                                    |
      | {"instrumentId": 47, "side": "BUY", "type": "MARKET", "quantity": 1, "amount": "100.00"} |
      | {"instrumentId": 47, "side": "BUY", "type": "MARKET"}                                    |

  @CU-03.27
  Scenario Outline: Only BUY and SELL, MARKET and LIMIT are accepted
    When user 1 places an order:
      | instrument | PAMP     |
      | side       | <side>   |
      | type       | <type>   |
      | quantity   | 1        |
    Then the response status is 400
    And the error code is "VALIDATION_ERROR"
    And the error detail is "<detail>"
    And no order is stored

    Examples:
      | side    | type   | detail                                                    |
      | HOLD    | MARKET | Request validation failed: 'side' must be BUY or SELL.     |
      | CASH_IN | MARKET | Request validation failed: 'side' must be BUY or SELL.     |
      | BUY     | STOP   | Request validation failed: 'type' must be MARKET or LIMIT. |

  @CU-03.28
  Scenario: Cash cannot be bought or sold
    When user 1 places an order:
      | instrument | ARS    |
      | side       | BUY    |
      | type       | MARKET |
      | quantity   | 1      |
    Then the response status is 400
    And the error code is "VALIDATION_ERROR"
    And the error detail is "Request validation failed: 'instrumentId' must reference a tradable instrument; ARS cannot be bought or sold."
    And no order is stored

  @CU-03.29
  Scenario: The idempotency key is required
    When user 1 places an order without idempotency key:
      | instrument | PAMP   |
      | side       | BUY    |
      | type       | MARKET |
      | quantity   | 1      |
    Then the response status is 400
    And the error code is "MISSING_IDEMPOTENCY_KEY"
    And no order is stored

  @CU-03.30
  Scenario: An order cannot name another user
    When user 2 places an order:
      | instrument | PAMP   |
      | side       | BUY    |
      | type       | MARKET |
      | quantity   | 1      |
      | userId     | 1      |
    Then the response status is 400
    And the error code is "VALIDATION_ERROR"
    And the error detail is "Request validation failed: 'userId' is not a recognized field."
    And no order is stored

  # ---------------------------------------------------------------- Idempotency and concurrency

  @CU-03.31
  Scenario: Resending the same order returns the original result
    Given user 1 placed an order with idempotency key "k1":
      | instrument | PAMP   |
      | side       | BUY    |
      | type       | MARKET |
      | quantity   | 10     |
    When user 1 sends the same order again
    Then the response is a replay of the original order
    And the available cash of user 1 is "618241.50"
    And user 1 holds 50 shares of "PAMP"

  @CU-03.32
  Scenario: Reusing a key with different content is a conflict
    Given user 1 placed an order with idempotency key "k1":
      | instrument | PAMP   |
      | side       | BUY    |
      | type       | MARKET |
      | quantity   | 10     |
    When user 1 places an order with idempotency key "k1":
      | instrument | PAMP   |
      | side       | BUY    |
      | type       | MARKET |
      | quantity   | 20     |
    Then the response status is 422
    And the error code is "IDEMPOTENCY_CONFLICT"
    And the error detail is "Idempotency key 'k1' was already used for a different request."
    And user 1 holds 50 shares of "PAMP"

  @CU-03.33
  Scenario: The same key used by another user is a different order
    Given user 1 placed an order with idempotency key "k1":
      | instrument | PAMP   |
      | side       | BUY    |
      | type       | MARKET |
      | quantity   | 10     |
    When user 2 places an order with idempotency key "k1":
      | instrument | PAMP   |
      | side       | BUY    |
      | type       | MARKET |
      | quantity   | 10     |
    Then the order is created with status "REJECTED"
    And the order rejection is "INSUFFICIENT_FUNDS" with message "Insufficient available cash in account 10002: required ARS 9258.50, available ARS 0.00."

  @CU-03.34
  Scenario: A rejected order is not re-evaluated when it is resent
    Given user 1 placed an order with idempotency key "k2":
      | instrument | PAMP   |
      | side       | BUY    |
      | type       | MARKET |
      | quantity   | 1000   |
    And user 1 made a deposit of "1000000.00" with idempotency key "d-k2"
    When user 1 sends the same order again
    Then the response is a replay of the original order
    And the order status is "REJECTED"
    And the order rejection is "INSUFFICIENT_FUNDS" with message "Insufficient available cash in account 10001: required ARS 925850.00, available ARS 627500.00."

  @CU-03.35
  Scenario: An invalid request does not consume its idempotency key
    When user 1 places an order with idempotency key "k3":
      | instrument | PAMP   |
      | side       | BUY    |
      | type       | MARKET |
      | quantity   | 0      |
    Then the response status is 400
    When user 1 places an order with idempotency key "k3":
      | instrument | PAMP   |
      | side       | BUY    |
      | type       | MARKET |
      | quantity   | 1      |
    Then the order is created with status "FILLED"

  @CU-03.36
  Scenario: Two simultaneous orders never spend the same cash
    When user 1 places these orders at the same time:
      | instrument | side | type   | quantity | idempotency key |
      | PAMP       | BUY  | MARKET | 400      | c1              |
      | PAMP       | BUY  | MARKET | 400      | c2              |
    Then one order is "FILLED" and the other is "REJECTED" with reason "INSUFFICIENT_FUNDS"
    And the available cash of user 1 is "257160.00"

  @CU-03.41
  Scenario: Identical content with different keys creates two orders
    When user 1 places an order with idempotency key "k10":
      | instrument | PAMP   |
      | side       | BUY    |
      | type       | MARKET |
      | quantity   | 10     |
    And user 1 places an order with idempotency key "k11":
      | instrument | PAMP   |
      | side       | BUY    |
      | type       | MARKET |
      | quantity   | 10     |
    Then the order is created with status "FILLED"
    And the available cash of user 1 is "608983.00"
    And user 1 holds 60 shares of "PAMP"

  @CU-03.42
  Scenario: One idempotency key per user across all operations
    Given user 1 made a deposit of "1000.00" with idempotency key "d1"
    When user 1 places an order with idempotency key "d1":
      | instrument | PAMP   |
      | side       | BUY    |
      | type       | MARKET |
      | quantity   | 1      |
    Then the response status is 422
    And the error code is "IDEMPOTENCY_CONFLICT"

  @CU-03.37
  Scenario: Orders are timestamped in UTC
    Given the current time is "2026-11-21T14:36:00Z"
    When user 1 places an order:
      | instrument | PAMP   |
      | side       | BUY    |
      | type       | MARKET |
      | quantity   | 1      |
    Then the order timestamp is "2026-11-21T14:36:00Z"
