package com.cocos.challenge.infrastructure.adapter.in.rest.order;

import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.infrastructure.adapter.in.rest.identity.RequesterId;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderQueryController {

    @GetMapping
    public OrderHistoryPageResponse list(@RequesterId UserId userId,
            @RequestParam(required = false) String status, @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        throw new UnsupportedOperationException();
    }

    @GetMapping("/{orderId}")
    public OrderRecordResponse get(@RequesterId UserId userId, @PathVariable @Positive long orderId) {
        throw new UnsupportedOperationException();
    }

    @PostMapping("/{orderId}/cancel")
    public CancellationResponse cancel(@RequesterId UserId userId, @PathVariable @Positive long orderId) {
        throw new UnsupportedOperationException();
    }
}
