package com.cocos.challenge.conventions;

import com.cocos.challenge.domain.exception.IdempotencyConflictException;
import com.cocos.challenge.domain.exception.InvalidRequestException;
import com.cocos.challenge.domain.exception.NotFoundException;
import com.cocos.challenge.domain.exception.OrderNotCancellableException;
import com.cocos.challenge.domain.model.OrderStatus;
import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.infrastructure.adapter.in.rest.format.ApiDecimals;
import com.cocos.challenge.infrastructure.adapter.in.rest.format.DecimalString;
import com.cocos.challenge.infrastructure.adapter.in.rest.idempotency.IdempotencyKeyHeader;
import com.cocos.challenge.infrastructure.adapter.in.rest.idempotency.IdempotentResponses;
import com.cocos.challenge.infrastructure.adapter.in.rest.identity.RequesterId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Test-only probes of spec 003. Imported only by the convention integration tests, never component-scanned.
 */
@TestConfiguration
@Import({ProbeControllers.ConventionsProbeController.class, ProbeControllers.InternalProbeController.class})
public class ProbeControllers {

    @RestController
    @RequestMapping("/test/conventions")
    static class ConventionsProbeController {

        private static final Logger log = LoggerFactory.getLogger(ConventionsProbeController.class);

        @GetMapping("/user")
        Map<String, Long> user(@RequesterId UserId userId) {
            return Map.of("userId", userId.value());
        }

        @PostMapping("/idempotent")
        ResponseEntity<Map<String, String>> idempotent(@IdempotencyKeyHeader String key) {
            return IdempotentResponses.created(Map.of("key", key));
        }

        @PostMapping("/replayed")
        ResponseEntity<Map<String, String>> replayed() {
            return IdempotentResponses.replayed(Map.of("key", "replayed"));
        }

        @GetMapping("/log")
        ResponseEntity<Void> log() {
            log.info("Conventions log probe");
            return ResponseEntity.noContent().build();
        }

        @PostMapping("/validate")
        ValidationRequest validate(@Valid @RequestBody ValidationRequest request) {
            return request;
        }

        @GetMapping("/format")
        Map<String, Object> format() {
            return Map.of("amounts", List.of(ApiDecimals.format(new BigDecimal("925.85")),
                    ApiDecimals.format(new BigDecimal("50000")), ApiDecimals.format(new BigDecimal("-0.45"))),
                    "at", Instant.parse("2026-11-21T14:36:00Z"));
        }

        @GetMapping("/required-param")
        Map<String, Integer> requiredParam(@RequestParam("count") Integer count) {
            return Map.of("count", count);
        }

        @GetMapping("/throw/{kind}")
        void throwException(@PathVariable String kind) {
            switch (kind) {
                case "not-found" -> throw new NotFoundException("Order 1234 was not found.");
                case "not-found-subclass" -> throw new NotFoundException("Order subclass was not found.") {
                };
                case "idempotency-conflict" -> throw new IdempotencyConflictException("k1");
                case "order-not-cancellable" -> throw new OrderNotCancellableException(1234, OrderStatus.FILLED);
                case "invalid-request" -> throw new InvalidRequestException("The request is invalid.");
                default -> throw new IllegalStateException("secret internal detail");
            }
        }
    }

    @RestController
    @RequestMapping("/api/internal/probe")
    @ConditionalOnProperty(name = "cocos.internal-tools.enabled", havingValue = "true")
    static class InternalProbeController {

        @GetMapping
        ResponseEntity<Void> probe() {
            return ResponseEntity.noContent().build();
        }
    }

    record ValidationRequest(@NotNull @DecimalString String amount, @NotNull @Positive Integer quantity) {
    }
}
