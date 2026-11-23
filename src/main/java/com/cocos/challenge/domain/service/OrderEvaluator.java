package com.cocos.challenge.domain.service;

import com.cocos.challenge.domain.model.Account;
import com.cocos.challenge.domain.model.CashBalance;
import com.cocos.challenge.domain.model.Instrument;
import com.cocos.challenge.domain.model.MarketQuote;
import com.cocos.challenge.domain.model.OrderEvaluation;
import com.cocos.challenge.domain.model.OrderRequest;
import com.cocos.challenge.domain.model.ShareBalance;

import java.util.Optional;

/**
 * Pure business rules that decide the outcome of an order (spec 005, plan.md § Order evaluation).
 */
public class OrderEvaluator {

    public OrderEvaluation evaluate(
            OrderRequest request, Instrument instrument, Optional<MarketQuote> quote, Account account,
            CashBalance cash, ShareBalance shares) {
        throw new UnsupportedOperationException("Order evaluation is not implemented yet.");
    }
}
