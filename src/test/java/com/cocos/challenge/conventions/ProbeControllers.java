package com.cocos.challenge.conventions;

import com.cocos.challenge.domain.exception.IdempotencyConflictException;
import com.cocos.challenge.domain.exception.InvalidRequestException;
import com.cocos.challenge.domain.exception.NotFoundException;
import com.cocos.challenge.domain.exception.OrderNotCancellableException;
import com.cocos.challenge.domain.model.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only probes of spec 003. Imported only by the convention integration tests, never component-scanned.
 */
@TestConfiguration
@Import(ProbeControllers.ConventionsProbeController.class)
public class ProbeControllers {

    @RestController
    @RequestMapping("/test/conventions")
    static class ConventionsProbeController {

        @PostMapping("/validate")
        void validate(@Valid @RequestBody ValidationRequest request) {
            // The error tests only need the validation and content-negotiation behavior of this endpoint.
        }

        @GetMapping("/throw/{kind}")
        void throwException(@PathVariable String kind) {
            switch (kind) {
                case "not-found" -> throw new NotFoundException("Order 1234 was not found.");
                case "idempotency-conflict" -> throw new IdempotencyConflictException("k1");
                case "order-not-cancellable" -> throw new OrderNotCancellableException(1234, OrderStatus.FILLED);
                case "invalid-request" -> throw new InvalidRequestException("The request is invalid.");
                default -> throw new IllegalStateException("secret internal detail");
            }
        }
    }

    record ValidationRequest(@NotNull String amount, @NotNull @Positive Integer quantity) {
    }
}
