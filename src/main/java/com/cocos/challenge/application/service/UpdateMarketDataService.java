package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.UpdateMarketDataUseCase;
import com.cocos.challenge.application.port.out.InstrumentRepository;
import com.cocos.challenge.application.port.out.MarketDataWriter;
import com.cocos.challenge.domain.exception.InvalidRequestException;
import com.cocos.challenge.domain.exception.NotFoundException;
import com.cocos.challenge.domain.model.Instrument;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.InstrumentType;
import com.cocos.challenge.domain.model.Money;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

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
        if (close == null || !close.isGreaterThan(Money.ZERO)) {
            throw new InvalidRequestException("Request validation failed: 'close' must be greater than 0.");
        }
        if (previousClose != null && !previousClose.isGreaterThan(Money.ZERO)) {
            throw new InvalidRequestException("Request validation failed: 'previousClose' must be greater than 0.");
        }
        Instrument instrument = instruments.findById(instrumentId)
                .orElseThrow(() -> new NotFoundException("Instrument " + instrumentId.value() + " was not found."));
        if (instrument.type() == InstrumentType.MONEDA) {
            throw new InvalidRequestException(
                    "Request validation failed: 'instrumentId' must reference a tradable instrument; ARS has no market data.");
        }
        MarketDataWriter.StoredMarketData stored = marketDataWriter.upsert(
                instrumentId, close, previousClose, LocalDate.now(clock));
        return new MarketDataResult(instrument.id(), instrument.ticker(), stored.date(), stored.close(),
                stored.previousClose());
    }
}
