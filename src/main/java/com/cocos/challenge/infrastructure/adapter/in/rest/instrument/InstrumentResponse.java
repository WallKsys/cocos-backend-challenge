package com.cocos.challenge.infrastructure.adapter.in.rest.instrument;

import com.cocos.challenge.domain.model.InstrumentSummary;
import com.cocos.challenge.domain.model.MarketQuote;
import com.cocos.challenge.infrastructure.adapter.in.rest.format.ApiDecimals;

public record InstrumentResponse(long id, String ticker, String name, String type, String lastPrice,
        String dailyChangePct) {

    static InstrumentResponse from(InstrumentSummary instrument) {
        MarketQuote quote = instrument.quote();
        return new InstrumentResponse(instrument.id().value(), instrument.ticker(), instrument.name(),
                instrument.type().name(), quote == null ? null : ApiDecimals.format(quote.close()),
                quote == null ? null : quote.dailyChange()
                        .map(change -> ApiDecimals.format(change.value())).orElse(null));
    }
}
