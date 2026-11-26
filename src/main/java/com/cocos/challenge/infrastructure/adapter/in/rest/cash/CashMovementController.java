package com.cocos.challenge.infrastructure.adapter.in.rest.cash;

import com.cocos.challenge.application.port.in.CashMovementCommand;
import com.cocos.challenge.application.port.in.CashMovementResult;
import com.cocos.challenge.application.port.in.DepositCashUseCase;
import com.cocos.challenge.application.port.in.WithdrawCashUseCase;
import com.cocos.challenge.domain.model.CashMovementRequest;
import com.cocos.challenge.domain.model.OrderSide;
import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.infrastructure.adapter.in.rest.idempotency.IdempotencyKeyHeader;
import com.cocos.challenge.infrastructure.adapter.in.rest.idempotency.IdempotentResponses;
import com.cocos.challenge.infrastructure.adapter.in.rest.identity.RequesterId;
import com.cocos.challenge.infrastructure.adapter.in.rest.tracing.OperationLog;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CashMovementController {

    private final DepositCashUseCase deposits;
    private final WithdrawCashUseCase withdrawals;

    public CashMovementController(DepositCashUseCase deposits, WithdrawCashUseCase withdrawals) {
        this.deposits = deposits;
        this.withdrawals = withdrawals;
    }

    @PostMapping("/api/v1/cash/deposits")
    public ResponseEntity<CashMovementResponse> deposit(@RequesterId UserId userId, @IdempotencyKeyHeader String key,
                                                        @Valid @RequestBody CashMovementRequestBody body) {
        var request = CashMovementRequest.of(OrderSide.CASH_IN, body.amount());
        var result = deposits.deposit(new CashMovementCommand(userId, key, request));
        OperationLog.cashDeposited(userId, key, result);
        return response(result);
    }

    @PostMapping("/api/v1/cash/withdrawals")
    public ResponseEntity<CashMovementResponse> withdraw(@RequesterId UserId userId, @IdempotencyKeyHeader String key,
                                                         @Valid @RequestBody CashMovementRequestBody body) {
        var request = CashMovementRequest.of(OrderSide.CASH_OUT, body.amount());
        var result = withdrawals.withdraw(new CashMovementCommand(userId, key, request));
        OperationLog.cashWithdrawn(userId, key, result);
        return response(result);
    }

    private ResponseEntity<CashMovementResponse> response(CashMovementResult result) {
        var body = CashMovementResponse.from(result.movement());
        return result.replayed() ? IdempotentResponses.replayed(body) : IdempotentResponses.created(body);
    }
}
