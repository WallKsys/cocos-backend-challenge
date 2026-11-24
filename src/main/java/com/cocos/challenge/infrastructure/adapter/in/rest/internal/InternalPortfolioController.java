package com.cocos.challenge.infrastructure.adapter.in.rest.internal;

import com.cocos.challenge.infrastructure.adapter.in.rest.portfolio.PortfolioResponse;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "cocos.internal-tools.enabled", havingValue = "true")
public class InternalPortfolioController {

    @GetMapping("/api/internal/users/{userId}/portfolio")
    public PortfolioResponse get(@PathVariable @Positive int userId) {
        throw new UnsupportedOperationException();
    }
}
