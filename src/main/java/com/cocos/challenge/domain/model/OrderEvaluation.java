package com.cocos.challenge.domain.model;

/**
 * Outcome of evaluating an order: status, executed quantity, price (null without market price) and,
 * when rejected, the reason and message.
 */
public record OrderEvaluation(OrderStatus status, long quantity, Money price, Rejection rejection) {
}
