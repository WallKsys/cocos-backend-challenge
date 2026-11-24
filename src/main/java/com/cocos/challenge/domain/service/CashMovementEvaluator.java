package com.cocos.challenge.domain.service;

import com.cocos.challenge.domain.model.Account;
import com.cocos.challenge.domain.model.CashBalance;
import com.cocos.challenge.domain.model.CashMovementRequest;
import com.cocos.challenge.domain.model.OrderStatus;
import com.cocos.challenge.domain.model.Rejection;

public final class CashMovementEvaluator {

    public Evaluation evaluate(CashMovementRequest request, Account account, CashBalance cash) {
        throw new UnsupportedOperationException();
    }

    public record Evaluation(OrderStatus status, Rejection rejection) {
    }
}
