package com.cocos.challenge.infrastructure.adapter.in.rest.internal;

import com.cocos.challenge.application.port.in.UpdateMarketDataUseCase;
import com.cocos.challenge.infrastructure.adapter.in.rest.format.ApiDecimals;

import java.time.LocalDate;

public record MarketDataView(
        long instrumentId, String ticker, LocalDate date, String close, String previousClose) {

    static MarketDataView from(UpdateMarketDataUseCase.MarketDataResult result) {
        return new MarketDataView(
                result.instrumentId().value(),
                result.ticker(),
                result.date(),
                ApiDecimals.format(result.close()),
                result.previousClose() == null ? null : ApiDecimals.format(result.previousClose()));
    }
}
