package com.cocos.challenge.application.port.out;

import com.cocos.challenge.domain.model.CashMovement;

public interface CashMovementRepository {

    CashMovement findById(long id);

    CashMovement save(NewCashMovement movement);
}
