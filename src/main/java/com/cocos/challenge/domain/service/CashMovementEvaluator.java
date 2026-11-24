package com.cocos.challenge.domain.service;

import com.cocos.challenge.domain.model.Account;
import com.cocos.challenge.domain.model.CashBalance;
import com.cocos.challenge.domain.model.CashMovementRequest;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.domain.model.OrderSide;
import com.cocos.challenge.domain.model.OrderStatus;
import com.cocos.challenge.domain.model.Rejection;
import com.cocos.challenge.domain.model.RejectionReason;

public final class CashMovementEvaluator {

    public Evaluation evaluate(CashMovementRequest request, Account account, CashBalance cash) {
        if (request.side() == OrderSide.CASH_IN) {
            return new Evaluation(OrderStatus.FILLED, null);
        }
        Money available = cash.available();
        if (request.amount().isGreaterThan(available)) {
            return new Evaluation(OrderStatus.REJECTED, new Rejection(RejectionReason.INSUFFICIENT_FUNDS,
                    "Insufficient available cash in account " + account.accountNumber()
                            + ": required ARS " + request.amount().amount().toPlainString()
                            + ", available ARS " + available.amount().toPlainString() + "."));
        }
        return new Evaluation(OrderStatus.FILLED, null);
    }

    public record Evaluation(OrderStatus status, Rejection rejection) {
    }
}
