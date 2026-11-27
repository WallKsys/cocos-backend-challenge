package com.cocos.challenge.infrastructure.adapter.in.rest.instrument;

import com.cocos.challenge.domain.model.CursorPage;
import com.cocos.challenge.domain.model.InstrumentSummary;
import com.cocos.challenge.domain.model.MarketQuote;
import com.cocos.challenge.domain.model.SearchCursor;
import com.cocos.challenge.infrastructure.adapter.in.rest.format.ApiDecimals;

import java.util.List;

public record InstrumentPageResponse(List<InstrumentResponse> items, String nextCursor) {

    static InstrumentPageResponse from(CursorPage<InstrumentSummary, SearchCursor> page) {
        return new InstrumentPageResponse(page.items().stream().map(InstrumentResponse::from).toList(),
                page.nextCursor() == null ? null : page.nextCursor().encode());
    }

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
}
