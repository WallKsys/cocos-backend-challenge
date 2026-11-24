package com.cocos.challenge.infrastructure.adapter.in.rest.cash;

import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.infrastructure.adapter.in.rest.idempotency.IdempotencyKeyHeader;
import com.cocos.challenge.infrastructure.adapter.in.rest.identity.RequesterId;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CashMovementController {

    @PostMapping("/api/v1/cash/deposits")
    public ResponseEntity<CashMovementResponse> deposit(@RequesterId UserId userId, @IdempotencyKeyHeader String key,
                                                        @Valid @RequestBody CashMovementRequestBody body) {
        throw new UnsupportedOperationException();
    }

    @PostMapping("/api/v1/cash/withdrawals")
    public ResponseEntity<CashMovementResponse> withdraw(@RequesterId UserId userId, @IdempotencyKeyHeader String key,
                                                         @Valid @RequestBody CashMovementRequestBody body) {
        throw new UnsupportedOperationException();
    }
}
