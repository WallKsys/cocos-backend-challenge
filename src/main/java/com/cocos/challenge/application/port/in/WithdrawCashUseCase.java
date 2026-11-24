package com.cocos.challenge.application.port.in;

public interface WithdrawCashUseCase {

    CashMovementResult withdraw(CashMovementCommand command);
}
