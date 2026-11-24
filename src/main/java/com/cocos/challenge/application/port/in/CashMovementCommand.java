package com.cocos.challenge.application.port.in;

import com.cocos.challenge.domain.model.CashMovementRequest;
import com.cocos.challenge.domain.model.UserId;

public record CashMovementCommand(UserId userId, String idempotencyKey, CashMovementRequest request) {
}
