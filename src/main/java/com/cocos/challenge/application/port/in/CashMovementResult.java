package com.cocos.challenge.application.port.in;

import com.cocos.challenge.domain.model.CashMovement;

public record CashMovementResult(CashMovement movement, boolean replayed) {
}
