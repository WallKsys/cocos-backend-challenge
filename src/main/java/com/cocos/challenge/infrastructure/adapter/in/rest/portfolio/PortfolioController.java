package com.cocos.challenge.infrastructure.adapter.in.rest.portfolio;

import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.infrastructure.adapter.in.rest.identity.RequesterId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PortfolioController {

    @GetMapping("/api/v1/portfolio")
    public PortfolioResponse get(@RequesterId UserId userId) {
        throw new UnsupportedOperationException();
    }
}
