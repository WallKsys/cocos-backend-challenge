package com.cocos.challenge.application.port.in;

import com.cocos.challenge.domain.model.CreatedUser;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.domain.model.OrderRecord;

import java.time.LocalDate;

public interface InternalToolsUseCase {

    CreatedUser createUser(String email);

    void deleteOrder(long orderId);

    OrderRecord executeOrder(long orderId);

    void restoreSeedData();

    MarketDataResult updateMarketData(InstrumentId instrumentId, Money close, Money previousClose);

    record MarketDataResult(
            InstrumentId instrumentId, String ticker, LocalDate date, Money close, Money previousClose) {
    }
}
