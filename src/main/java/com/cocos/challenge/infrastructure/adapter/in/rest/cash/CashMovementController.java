package com.cocos.challenge.infrastructure.adapter.in.rest.cash;

import com.cocos.challenge.application.port.in.CashMovementUseCase;
import com.cocos.challenge.application.port.in.CashMovementUseCase.CashMovementCommand;
import com.cocos.challenge.application.port.in.CashMovementUseCase.CashMovementResult;
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

    private final CashMovementUseCase movements;

    public CashMovementController(CashMovementUseCase movements) {
        this.movements = movements;
    }

    @PostMapping("/api/v1/cash/deposits")
    public ResponseEntity<CashMovementResponse> deposit(@RequesterId UserId userId, @IdempotencyKeyHeader String key,
                                                        @Valid @RequestBody CashMovementRequestBody body) {
        var request = CashMovementRequest.of(OrderSide.CASH_IN, body.amount());
        var result = movements.deposit(new CashMovementCommand(userId, key, request));
        OperationLog.cashDeposited(userId, key, result);
        return response(result);
    }

    @PostMapping("/api/v1/cash/withdrawals")
    public ResponseEntity<CashMovementResponse> withdraw(@RequesterId UserId userId, @IdempotencyKeyHeader String key,
                                                         @Valid @RequestBody CashMovementRequestBody body) {
        var request = CashMovementRequest.of(OrderSide.CASH_OUT, body.amount());
        var result = movements.withdraw(new CashMovementCommand(userId, key, request));
        OperationLog.cashWithdrawn(userId, key, result);
        return response(result);
    }

    private ResponseEntity<CashMovementResponse> response(CashMovementResult result) {
        var body = CashMovementResponse.from(result.movement());
        return result.replayed() ? IdempotentResponses.replayed(body) : IdempotentResponses.created(body);
    }
}
