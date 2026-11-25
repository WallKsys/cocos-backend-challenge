package com.cocos.challenge.infrastructure.adapter.in.rest.internal;

import com.cocos.challenge.application.port.in.GetOrderHistoryQuery;
import com.cocos.challenge.domain.model.OrderHistoryCriteria;
import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.infrastructure.adapter.in.rest.order.OrderHistoryPageResponse;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "cocos.internal-tools.enabled", havingValue = "true")
public class InternalOrderHistoryController {

    private final GetOrderHistoryQuery history;

    public InternalOrderHistoryController(GetOrderHistoryQuery history) {
        this.history = history;
    }

    @GetMapping("/api/internal/users/{userId}/orders")
    public OrderHistoryPageResponse list(@PathVariable @Positive int userId,
            @RequestParam(required = false) String status, @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        var criteria = OrderHistoryCriteria.of(status, limit, cursor);
        return OrderHistoryPageResponse.from(history.list(new UserId(userId), criteria));
    }
}
