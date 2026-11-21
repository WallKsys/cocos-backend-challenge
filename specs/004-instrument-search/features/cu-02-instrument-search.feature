# Acceptance scenarios CU-02 (docs/casos-de-uso.md). Spec 004. Do not edit (Constitution Principle III).
# Data: seed of the challenge (65 tradable instruments + ARS). Expected results were computed from that seed.

Feature: Instrument search
  Users search the market by ticker and/or name to find instruments to trade.
  Matching ignores case and accents, partial matching needs at least 3 characters,
  and cash (ARS) is never returned.

  @CU-02.01
  Scenario: Partial match on the instrument name
    When I search instruments for "gal"
    Then the result tickers are "GGAL"

  @CU-02.02
  Scenario: Matching ignores accents
    When I search instruments for "rio"
    Then the result tickers are "BHIP, MOLI, OEST, RICH"

  @CU-02.03
  Scenario Outline: Matching ignores case
    When I search instruments for "<text>"
    Then the result tickers are "BBAR, BHIP, BMA, BPAT"

    Examples:
      | text  |
      | BANCO |
      | banco |

  @CU-02.04
  Scenario: An exact ticker match comes first
    When I search instruments for "pamp"
    Then the first result ticker is "PAMP"

  @CU-02.05
  Scenario: Results are ranked by exact ticker, ticker prefix, then other matches
    When I search instruments for "ban"
    Then the result tickers are "BBAR, BHIP, BMA, BPAT, GBAN"

  @CU-02.05b
  Scenario Outline: One or two characters only match an exact ticker
    When I search instruments for "<text>"
    Then the result is empty

    Examples:
      | text |
      | b    |
      | ba   |

  @CU-02.05c
  Scenario: A short exact ticker is found without returning partial matches
    Given an instrument with ticker "TS" and name "Tenaris S.A." exists
    And an instrument with ticker "TSLA" and name "Tesla Inc. CEDEAR" exists
    When I search instruments for "ts"
    Then the result tickers are "TS"

  @CU-02.06
  Scenario: Ticker and name filters must both match
    When I search instruments with ticker filter "bma" and name filter "banco"
    Then the result tickers are "BMA"

  @CU-02.07
  Scenario: Searching with no criteria lists the whole market alphabetically
    When I search instruments with no criteria
    Then the result contains 20 instruments
    And the result tickers start with "AGRO, ALUA, AUSO, BBAR, BHIP, BMA"
    And there is a next page

  @CU-02.08
  Scenario Outline: Cash is never returned
    When I search instruments for "<text>"
    Then the result is empty

    Examples:
      | text  |
      | ars   |
      | pesos |

  @CU-02.09
  Scenario: A search without matches returns an empty list
    When I search instruments for "xyz123"
    Then the response status is 200
    And the result is empty

  @CU-02.10
  Scenario: An instrument without market data has no price
    When I search instruments for "PGR"
    Then the result tickers are "PGR"
    And the instrument "PGR" has no last price and no daily change

  @CU-02.11
  Scenario: Each result includes its market data
    When I search instruments for "pamp"
    Then the instrument "PAMP" has name "Pampa Holding S.A.", type "ACCIONES", last price "925.85" and daily change "0.44"

  @CU-02.12
  Scenario: Paging through the market returns every instrument once
    When I list all instruments page by page with a page size of 20
    Then I receive pages with 20, 20, 20 and 5 instruments
    And the instruments received are the 65 tradable instruments in alphabetical order, without repetitions

  @CU-02.13
  Scenario Outline: The name filter requires at least 3 characters
    When I search instruments with name filter "<name>"
    Then the response status is 400
    And the error code is "SEARCH_TERM_TOO_SHORT"
    And the error detail is "The name filter requires at least 3 characters."

    Examples:
      | name |
      | b    |
      | ba   |

  @CU-02.14
  Scenario: General term combined with a ticker filter
    When I search instruments for "banco" with ticker filter "bma"
    Then the result tickers are "BMA"

  @CU-02.15
  Scenario: A two-character ticker filter only matches an exact ticker
    When I search instruments for "banco" with ticker filter "bm"
    Then the result is empty
