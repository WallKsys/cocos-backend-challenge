package com.cocos.challenge.application.port.out;

import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.MarketQuote;
import com.cocos.challenge.domain.model.Money;

import java.time.LocalDate;
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

    StoredMarketData upsert(InstrumentId instrumentId, Money close, Money previousClose, LocalDate date);

    record StoredMarketData(LocalDate date, Money close, Money previousClose) {
    }
}
