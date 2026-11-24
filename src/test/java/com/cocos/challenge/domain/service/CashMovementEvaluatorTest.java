package com.cocos.challenge.domain.service;

import com.cocos.challenge.domain.model.Account;
import com.cocos.challenge.domain.model.CashBalance;
import com.cocos.challenge.domain.model.CashMovementRequest;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.domain.model.OrderSide;
import com.cocos.challenge.domain.model.OrderStatus;
import com.cocos.challenge.domain.model.Rejection;
import com.cocos.challenge.domain.model.RejectionReason;
import com.cocos.challenge.domain.model.UserId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class CashMovementEvaluatorTest {

    private final CashMovementEvaluator evaluator = new CashMovementEvaluator();
    private final Account account = new Account(new UserId(1), "10001");
    private final CashBalance cash = new CashBalance(Money.of("753000.00"), Money.of("125500.00"));

    @Test
    @Tag("CU-05.01")
    void fillsDepositWithoutExistingCash() {
        var request = CashMovementRequest.of(OrderSide.CASH_IN, "100000.00");
        var result = evaluator.evaluate(request, account, new CashBalance(Money.ZERO, Money.ZERO));

        assertThat(result.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(result.rejection()).isNull();
    }

    @Test
    @Tag("CU-06.01")
    void fillsWithdrawalWithinAvailableCash() {
        var result = evaluator.evaluate(CashMovementRequest.of(OrderSide.CASH_OUT, "100000.00"), account, cash);

        assertThat(result.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(result.rejection()).isNull();
    }

    @Test
    @Tag("CU-06.02")
    void rejectsWithdrawalThatWouldSpendReservedCash() {
        var result = evaluator.evaluate(CashMovementRequest.of(OrderSide.CASH_OUT, "700000.00"), account, cash);

        assertThat(result.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(result.rejection()).isEqualTo(new Rejection(RejectionReason.INSUFFICIENT_FUNDS,
                "Insufficient available cash in account 10001: required ARS 700000.00, available ARS 627500.00."));
    }

    @Test
    @Tag("CU-06.03")
    void fillsWithdrawalOfExactAvailableCashIncludingCents() {
        var request = CashMovementRequest.of(OrderSide.CASH_OUT, "618241.50");
        var balance = new CashBalance(Money.of("743741.50"), cash.reserved());
        var result = evaluator.evaluate(request, account, balance);

        assertThat(result.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(result.rejection()).isNull();
    }
}
