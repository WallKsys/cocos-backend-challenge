package com.cocos.challenge.application.port.out;

import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.Money;

import java.time.LocalDate;

public interface MarketDataWriter {

    StoredMarketData upsert(InstrumentId instrumentId, Money close, Money previousClose, LocalDate date);

    record StoredMarketData(LocalDate date, Money close, Money previousClose) {
    }
}
