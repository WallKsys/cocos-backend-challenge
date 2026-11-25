package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.UpdateMarketDataUseCase;
import com.cocos.challenge.application.port.out.InstrumentRepository;
import com.cocos.challenge.application.port.out.MarketDataWriter;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.Money;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

public class UpdateMarketDataService implements UpdateMarketDataUseCase {

    private final InstrumentRepository instruments;
    private final MarketDataWriter marketDataWriter;
    private final Clock clock;

    public UpdateMarketDataService(
            InstrumentRepository instruments, MarketDataWriter marketDataWriter, Clock clock) {
        this.instruments = instruments;
        this.marketDataWriter = marketDataWriter;
        this.clock = clock;
    }

    @Override
    @Transactional
    public MarketDataResult update(InstrumentId instrumentId, Money close, Money previousClose) {
        throw new UnsupportedOperationException();
    }
}
