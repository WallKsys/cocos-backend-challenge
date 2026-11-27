package com.cocos.challenge.infrastructure.adapter.in.rest.portfolio;

import com.cocos.challenge.domain.model.CashBalance;
import com.cocos.challenge.domain.model.Percentage;
import com.cocos.challenge.domain.model.Portfolio;
import com.cocos.challenge.domain.model.Portfolio.PortfolioWarning;
import com.cocos.challenge.domain.model.Position;
import com.cocos.challenge.infrastructure.adapter.in.rest.format.ApiDecimals;

import java.util.List;

public record PortfolioResponse(String totalValue, CashResponse cash, List<PositionResponse> positions,
                                List<WarningResponse> warnings) {

    public static PortfolioResponse from(Portfolio portfolio) {
        return new PortfolioResponse(ApiDecimals.format(portfolio.totalValue()), CashResponse.from(portfolio.cash()),
                portfolio.positions().stream().map(PositionResponse::from).toList(),
                portfolio.warnings().stream().map(WarningResponse::from).toList());
    }

    record CashResponse(String total, String reserved, String available) {

        static CashResponse from(CashBalance cash) {
            return new CashResponse(ApiDecimals.format(cash.cash()), ApiDecimals.format(cash.reserved()),
                    ApiDecimals.format(cash.available()));
        }
    }

    record PositionResponse(long instrumentId, String ticker, String name, long quantity, long reservedQuantity,
                            String lastPrice, String marketValue, String averageCost, String totalReturnPct,
                            String dailyChangePct, List<WarningResponse> warnings) {

        static PositionResponse from(Position position) {
            return new PositionResponse(position.instrumentId().value(), position.ticker(), position.name(),
                    position.quantity(), position.reservedQuantity(),
                    position.lastPrice().map(ApiDecimals::format).orElse(null),
                    position.marketValue().map(ApiDecimals::format).orElse(null),
                    position.averageCost().map(ApiDecimals::format).orElse(null),
                    position.totalReturnPct().map(Percentage::value).map(ApiDecimals::format).orElse(null),
                    position.dailyChangePct().map(Percentage::value).map(ApiDecimals::format).orElse(null),
                    position.warnings().stream().map(WarningResponse::from).toList());
        }
    }

    record WarningResponse(String code, String message) {

        static WarningResponse from(PortfolioWarning warning) {
            return new WarningResponse(warning.code().name(), warning.message());
        }
    }
}
