package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.CashMovementRepository;
import com.cocos.challenge.application.port.out.NewCashMovement;
import com.cocos.challenge.domain.model.CashMovement;
import com.cocos.challenge.domain.model.InstrumentId;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Optional;

public class JdbcCashMovementRepository implements CashMovementRepository {

    public JdbcCashMovementRepository(JdbcClient jdbc) {
    }

    @Override
    public CashMovement findById(long id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CashMovement save(NewCashMovement movement) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<InstrumentId> findCashInstrument() {
        throw new UnsupportedOperationException();
    }
}
