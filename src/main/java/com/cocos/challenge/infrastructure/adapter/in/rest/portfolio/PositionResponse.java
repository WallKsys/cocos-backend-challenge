package com.cocos.challenge.infrastructure.adapter.in.rest.portfolio;

import com.cocos.challenge.domain.model.Percentage;
import com.cocos.challenge.domain.model.Position;
import com.cocos.challenge.infrastructure.adapter.in.rest.format.ApiDecimals;

import java.util.List;

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
