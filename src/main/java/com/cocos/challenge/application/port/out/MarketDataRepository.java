package com.cocos.challenge.application.port.out;

import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.MarketQuote;

import java.util.Optional;

public interface MarketDataRepository {

    Optional<MarketQuote> latestQuote(InstrumentId instrumentId);
}
