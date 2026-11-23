package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.domain.model.CashBalance;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.domain.model.ShareBalance;
import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class JdbcLedgerReaderIT {

    @Autowired
    JdbcClient jdbc;

    @Test
    void readsSeededCashAndReservationsAndReturnsZeroForAnEmptyLedger() {
        JdbcLedgerReader reader = new JdbcLedgerReader(jdbc);
        assertThat(reader.cashBalance(new UserId(1)))
                .isEqualTo(new CashBalance(Money.of("753000.00"), Money.of("125500.00")));
        assertThat(reader.cashBalance(new UserId(2))).isEqualTo(new CashBalance(Money.ZERO, Money.ZERO));
    }

    @Test
    void readsSeededShareBalancesIncludingNegativeHoldings() {
        JdbcLedgerReader reader = new JdbcLedgerReader(jdbc);
        UserId user = new UserId(1);
        assertThat(reader.shareBalance(user, new InstrumentId(47))).isEqualTo(new ShareBalance(40, 0));
        assertThat(reader.shareBalance(user, new InstrumentId(54))).isEqualTo(new ShareBalance(500, 0));
        assertThat(reader.shareBalance(user, new InstrumentId(31))).isEqualTo(new ShareBalance(-10, 0));
        assertThat(reader.shareBalance(new UserId(2), new InstrumentId(47))).isEqualTo(new ShareBalance(0, 0));
    }

    @Test
    @Transactional
    void newLimitSellReservesSharesWithoutChangingCash() {
        JdbcLedgerReader reader = new JdbcLedgerReader(jdbc);
        UserId user = new UserId(1);
        CashBalance cashBefore = reader.cashBalance(user);

        jdbc.sql("""
                INSERT INTO orders (instrumentid, userid, size, price, side, status, type, datetime)
                VALUES (47, 1, 15, 950.00, 'SELL', 'NEW', 'LIMIT', CURRENT_TIMESTAMP)
                """).update();

        ShareBalance shares = reader.shareBalance(user, new InstrumentId(47));
        assertThat(shares).isEqualTo(new ShareBalance(40, 15));
        assertThat(shares.available()).isEqualTo(25);
        assertThat(reader.cashBalance(user)).isEqualTo(cashBefore);
    }
}
