package com.cocos.challenge.infrastructure.adapter.in.rest.portfolio;

import com.cocos.challenge.domain.model.Portfolio;
import com.cocos.challenge.infrastructure.adapter.in.rest.format.ApiDecimals;

import java.util.List;

public record PortfolioResponse(String totalValue, CashResponse cash, List<PositionResponse> positions,
                                List<WarningResponse> warnings) {

    public static PortfolioResponse from(Portfolio portfolio) {
        return new PortfolioResponse(ApiDecimals.format(portfolio.totalValue()), CashResponse.from(portfolio.cash()),
                portfolio.positions().stream().map(PositionResponse::from).toList(),
                portfolio.warnings().stream().map(WarningResponse::from).toList());
    }
}
