-- CM-13: two new rejection codes for BUY / SELL orders.
-- PRICE_OUT_OF_RANGE (DEC-29): a LIMIT price outside the band of 20 % around the last close.
-- QUANTITY_TOO_LARGE (DEC-30): an order by amount whose computed quantity exceeds 2147483647 shares.

ALTER TABLE orders
    DROP CONSTRAINT orders_rejection_code_check,
    ADD CONSTRAINT orders_rejection_code_check
        CHECK (rejection_code IS NULL
               OR rejection_code IN ('INSUFFICIENT_FUNDS', 'INSUFFICIENT_SHARES', 'AMOUNT_BELOW_PRICE',
                                     'NO_MARKET_PRICE', 'PRICE_OUT_OF_RANGE', 'QUANTITY_TOO_LARGE'));
