package com.cocos.challenge.application.port.out;

import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.MarketQuote;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface MarketDataRepository {

    Optional<MarketQuote> latestQuote(InstrumentId instrumentId);

    default Map<InstrumentId, MarketQuote> latestQuotes(Set<InstrumentId> instrumentIds) {
        Map<InstrumentId, MarketQuote> quotes = new HashMap<>();
        for (InstrumentId id : instrumentIds) {
            latestQuote(id).ifPresent(quote -> quotes.put(id, quote));
        }
        return quotes;
    }
}
