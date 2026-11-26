package com.cocos.challenge.application.port.in;

import com.cocos.challenge.domain.model.CashMovement;
import com.cocos.challenge.domain.model.CashMovementRequest;
import com.cocos.challenge.domain.model.UserId;

public interface CashMovementUseCase {

    CashMovementResult deposit(CashMovementCommand command);

    CashMovementResult withdraw(CashMovementCommand command);

    record CashMovementCommand(UserId userId, String idempotencyKey, CashMovementRequest request) {
    }

    record CashMovementResult(CashMovement movement, boolean replayed) {
    }
}
