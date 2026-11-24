package com.cocos.challenge.application.port.in;

public interface DepositCashUseCase {

    CashMovementResult deposit(CashMovementCommand command);
}
