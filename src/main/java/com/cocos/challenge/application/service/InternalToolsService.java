package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.InternalToolsUseCase;
import com.cocos.challenge.application.port.out.AccountRepository;
import com.cocos.challenge.application.port.out.AdminOrderRepository;
import com.cocos.challenge.application.port.out.InstrumentRepository;
import com.cocos.challenge.application.port.out.MarketDataRepository;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.application.port.out.SeedDataRestorer;
import com.cocos.challenge.domain.exception.InvalidRequestException;
import com.cocos.challenge.domain.exception.NotFoundException;
import com.cocos.challenge.domain.model.CreatedUser;
import com.cocos.challenge.domain.model.Instrument;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.InstrumentType;
import com.cocos.challenge.domain.model.Money;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

public class InternalToolsService implements InternalToolsUseCase {

    private final AccountRepository accounts;
    private final AdminOrderRepository adminOrders;
    private final OrderLedgerRepository ledger;
    private final InstrumentRepository instruments;
    private final MarketDataRepository marketData;
    private final SeedDataRestorer seedDataRestorer;
    private final Clock clock;

    public InternalToolsService(AccountRepository accounts, AdminOrderRepository adminOrders,
            OrderLedgerRepository ledger, InstrumentRepository instruments, MarketDataRepository marketData,
            SeedDataRestorer seedDataRestorer, Clock clock) {
        this.accounts = accounts;
        this.adminOrders = adminOrders;
        this.ledger = ledger;
        this.instruments = instruments;
        this.marketData = marketData;
        this.seedDataRestorer = seedDataRestorer;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CreatedUser createUser(String email) {
        return accounts.create(email);
    }

    @Override
    @Transactional
    public void restoreSeedData() {
        seedDataRestorer.restore();
    }

    @Override
    @Transactional
    public MarketDataResult updateMarketData(InstrumentId instrumentId, Money close, Money previousClose) {
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
        MarketDataRepository.StoredMarketData stored = marketData.upsert(
                instrumentId, close, previousClose, LocalDate.now(clock));
        return new MarketDataResult(instrument.id(), instrument.ticker(), stored.date(), stored.close(),
                stored.previousClose());
    }
}
