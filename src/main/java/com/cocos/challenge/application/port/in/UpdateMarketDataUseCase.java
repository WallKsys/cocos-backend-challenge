package com.cocos.challenge.application.port.in;

import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.Money;

import java.time.LocalDate;

public interface UpdateMarketDataUseCase {

    MarketDataResult update(InstrumentId instrumentId, Money close, Money previousClose);

    record MarketDataResult(
            InstrumentId instrumentId, String ticker, LocalDate date, Money close, Money previousClose) {
    }
}
