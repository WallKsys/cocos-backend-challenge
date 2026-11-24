package com.cocos.challenge.domain.model;

import com.cocos.challenge.domain.exception.InvalidRequestException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class CashMovementRequestTest {

    @Test
    @Tag("CU-05.02")
    void preservesCentsAndNormalizesTheFingerprint() {
        assertThat(CashMovementRequest.of(OrderSide.CASH_IN, "1234.56").amount()).isEqualTo(Money.of("1234.56"));
        assertThat(CashMovementRequest.of(OrderSide.CASH_IN, "100000").fingerprint())
                .isEqualTo(RequestFingerprint.of("CASH_IN|amount=100000.00"))
                .isNotEqualTo(CashMovementRequest.of(OrderSide.CASH_OUT, "100000.00").fingerprint());
    }

    @ParameterizedTest
    @CsvSource({"CASH_IN, 0.00", "CASH_IN, -100.00", "CASH_OUT, 0.00", "CASH_OUT, -100.00"})
    @Tag("CU-05.04")
    @Tag("CU-06.07")
    void rejectsNonPositiveAmounts(OrderSide side, String amount) {
        assertThatThrownBy(() -> CashMovementRequest.of(side, amount))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Request validation failed: 'amount' must be greater than 0.");
    }
}
