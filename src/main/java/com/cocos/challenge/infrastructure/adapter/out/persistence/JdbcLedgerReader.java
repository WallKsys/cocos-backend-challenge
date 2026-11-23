package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.LedgerReader;
import com.cocos.challenge.domain.model.CashBalance;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.ShareBalance;
import com.cocos.challenge.domain.model.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcLedgerReader implements LedgerReader {

    private final JdbcClient jdbc;

    public JdbcLedgerReader(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CashBalance cashBalance(UserId userId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ShareBalance shareBalance(UserId userId, InstrumentId instrumentId) {
        throw new UnsupportedOperationException();
    }
}
