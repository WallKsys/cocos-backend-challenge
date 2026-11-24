package com.cocos.challenge.infrastructure.adapter.in.rest.portfolio;

import com.cocos.challenge.application.port.in.GetPortfolioQuery;
import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.infrastructure.adapter.in.rest.identity.RequesterId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PortfolioController {

    private final GetPortfolioQuery portfolio;

    public PortfolioController(GetPortfolioQuery portfolio) {
        this.portfolio = portfolio;
    }

    @GetMapping("/api/v1/portfolio")
    public PortfolioResponse get(@RequesterId UserId userId) {
        return PortfolioResponse.from(portfolio.get(userId));
    }
}
