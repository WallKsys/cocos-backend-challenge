package com.cocos.challenge.domain.model;

import com.cocos.challenge.domain.exception.InvalidRequestException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Spec 005 / plan.md § Validation: the eight cross-field rules are checked in order, and the
 * request fingerprint is the canonical ORDER string of plan.md § Request fingerprint.
 */
@Tag("unit")
@Tag("CU-03.22")
@Tag("CU-03.24")
@Tag("CU-03.25")
@Tag("CU-03.26")
@Tag("CU-03.27")
class OrderRequestTest {

    @Test
    void acceptsABuyAtMarketByQuantity() {
        OrderRequest request = OrderRequest.of(47L, "BUY", "MARKET", 10, null, null);

        assertThat(request.instrumentId()).isEqualTo(new InstrumentId(47));
        assertThat(request.side()).isEqualTo(OrderSide.BUY);
        assertThat(request.type()).isEqualTo(OrderType.MARKET);
        assertThat(request.quantity()).isEqualTo(10L);
        assertThat(request.amount()).isNull();
        assertThat(request.price()).isNull();
        assertThat(request.byQuantity()).isTrue();
        assertThat(request.byAmount()).isFalse();
    }

    @Test
    void acceptsASellAtMarketByAmount() {
        OrderRequest request = OrderRequest.of(47L, "SELL", "MARKET", null, "50000.00", null);

        assertThat(request.side()).isEqualTo(OrderSide.SELL);
        assertThat(request.quantity()).isNull();
        assertThat(request.amount()).isEqualTo(Money.of("50000.00"));
        assertThat(request.byQuantity()).isFalse();
        assertThat(request.byAmount()).isTrue();
    }

    @Test
    void acceptsALimitOrderWithAPrice() {
        OrderRequest request = OrderRequest.of(47L, "BUY", "LIMIT", 100, null, "900");

        assertThat(request.type()).isEqualTo(OrderType.LIMIT);
        assertThat(request.price()).isEqualTo(Money.of("900.00"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"HOLD", "CASH_IN", "CASH_OUT", "buy"})
    void rejectsAnySideOtherThanBuyOrSell(String side) {
        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> OrderRequest.of(47L, side, "MARKET", 1, null, null))
                .withMessage("Request validation failed: 'side' must be BUY or SELL.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"STOP", "market"})
    void rejectsAnyTypeOtherThanMarketOrLimit(String type) {
        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> OrderRequest.of(47L, "BUY", type, 1, null, null))
                .withMessage("Request validation failed: 'type' must be MARKET or LIMIT.");
    }

    @Test
    void rejectsARequestWithoutQuantityOrAmount() {
        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> OrderRequest.of(47L, "BUY", "MARKET", null, null, null))
                .withMessage("Request validation failed: exactly one of 'quantity' or 'amount' is required.");
    }

    @Test
    void rejectsARequestWithBothQuantityAndAmount() {
        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> OrderRequest.of(47L, "BUY", "MARKET", 1, "100.00", null))
                .withMessage("Request validation failed: exactly one of 'quantity' or 'amount' is required.");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5})
    void rejectsANonPositiveQuantity(int quantity) {
        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> OrderRequest.of(47L, "BUY", "MARKET", quantity, null, null))
                .withMessage("Request validation failed: 'quantity' must be greater than 0.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-0.01", "-5"})
    void rejectsANonPositiveAmount(String amount) {
        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> OrderRequest.of(47L, "BUY", "MARKET", null, amount, null))
                .withMessage("Request validation failed: 'amount' must be greater than 0.");
    }

    @Test
    void rejectsALimitOrderWithoutPrice() {
        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> OrderRequest.of(47L, "BUY", "LIMIT", 1, null, null))
                .withMessage("Request validation failed: 'price' is required for LIMIT orders.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-0.01"})
    void rejectsALimitOrderWithANonPositivePrice(String price) {
        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> OrderRequest.of(47L, "BUY", "LIMIT", 1, null, price))
                .withMessage("Request validation failed: 'price' must be greater than 0.");
    }

    @Test
    void rejectsAMarketOrderWithPrice() {
        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> OrderRequest.of(47L, "BUY", "MARKET", 1, null, "900.00"))
                .withMessage("Request validation failed: 'price' must not be sent for MARKET orders.");
    }

    @Test
    void checksTheRulesInTheDocumentedOrder() {
        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> OrderRequest.of(47L, "HOLD", "STOP", null, null, null))
                .withMessage("Request validation failed: 'side' must be BUY or SELL.");

        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> OrderRequest.of(47L, "BUY", "STOP", 1, "100.00", null))
                .withMessage("Request validation failed: 'type' must be MARKET or LIMIT.");

        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> OrderRequest.of(47L, "BUY", "MARKET", 0, "100.00", "900.00"))
                .withMessage("Request validation failed: exactly one of 'quantity' or 'amount' is required.");

        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> OrderRequest.of(47L, "BUY", "LIMIT", 0, null, null))
                .withMessage("Request validation failed: 'quantity' must be greater than 0.");

        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> OrderRequest.of(47L, "BUY", "LIMIT", null, "0.00", null))
                .withMessage("Request validation failed: 'amount' must be greater than 0.");

        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> OrderRequest.of(47L, "BUY", "LIMIT", 1, null, null))
                .withMessage("Request validation failed: 'price' is required for LIMIT orders.");

        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> OrderRequest.of(47L, "BUY", "MARKET", 0, null, "900.00"))
                .withMessage("Request validation failed: 'quantity' must be greater than 0.");
    }

    @Test
    void fingerprintOfAMarketOrderByQuantityIsTheCanonicalString() {
        OrderRequest request = OrderRequest.of(47L, "BUY", "MARKET", 10, null, null);

        assertThat(request.fingerprint())
                .isEqualTo(RequestFingerprint.of("ORDER|instrumentId=47|side=BUY|type=MARKET|quantity=10|amount=|price="));
    }

    @Test
    void fingerprintOfALimitOrderByAmountIsTheCanonicalString() {
        OrderRequest request = OrderRequest.of(47L, "SELL", "LIMIT", null, "900", "950.5");

        assertThat(request.fingerprint()).isEqualTo(RequestFingerprint.of(
                "ORDER|instrumentId=47|side=SELL|type=LIMIT|quantity=|amount=900.00|price=950.50"));
    }

    @Test
    void fingerprintNormalizesEquivalentAmounts() {
        OrderRequest wholeNumber = OrderRequest.of(47L, "BUY", "LIMIT", null, "900", "950");
        OrderRequest twoDecimals = OrderRequest.of(47L, "BUY", "LIMIT", null, "900.00", "950.00");

        assertThat(wholeNumber.fingerprint()).isEqualTo(twoDecimals.fingerprint());
    }

    @Test
    void fingerprintChangesWithEveryRequestField() {
        RequestFingerprint base = OrderRequest.of(47L, "BUY", "MARKET", 10, null, null).fingerprint();

        assertThat(OrderRequest.of(48L, "BUY", "MARKET", 10, null, null).fingerprint()).isNotEqualTo(base);
        assertThat(OrderRequest.of(47L, "SELL", "MARKET", 10, null, null).fingerprint()).isNotEqualTo(base);
        assertThat(OrderRequest.of(47L, "BUY", "LIMIT", 10, null, "900.00").fingerprint()).isNotEqualTo(base);
        assertThat(OrderRequest.of(47L, "BUY", "MARKET", 11, null, null).fingerprint()).isNotEqualTo(base);
        assertThat(OrderRequest.of(47L, "BUY", "MARKET", null, "50000.00", null).fingerprint())
                .isNotEqualTo(base);
    }
}
