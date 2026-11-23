package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.LedgerReader;
import com.cocos.challenge.domain.model.CashBalance;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.domain.model.ShareBalance;
import com.cocos.challenge.domain.model.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcLedgerReader implements LedgerReader {

    static final String CASH_BALANCE_SQL = """
            SELECT COALESCE(SUM(CASE
                       WHEN status = 'FILLED' AND side = 'CASH_IN'  THEN  size
                       WHEN status = 'FILLED' AND side = 'CASH_OUT' THEN -size
                       WHEN status = 'FILLED' AND side = 'BUY'      THEN -size * price
                       WHEN status = 'FILLED' AND side = 'SELL'     THEN  size * price
                   END), 0) AS cash,
                   COALESCE(SUM(CASE WHEN status = 'NEW' AND side = 'BUY' THEN size * price END), 0) AS reserved
            FROM orders
            WHERE userid = :userId AND status IN ('FILLED', 'NEW')
            """;

    static final String SHARE_BALANCE_SQL = """
            SELECT COALESCE(SUM(CASE WHEN status = 'FILLED' AND side = 'BUY'  THEN  size
                                     WHEN status = 'FILLED' AND side = 'SELL' THEN -size END), 0) AS quantity,
                   COALESCE(SUM(CASE WHEN status = 'NEW' AND side = 'SELL' THEN size END), 0) AS reserved
            FROM orders
            WHERE userid = :userId AND instrumentid = :instrumentId AND status IN ('FILLED', 'NEW')
              AND side IN ('BUY', 'SELL')
            """;

    private final JdbcClient jdbc;

    public JdbcLedgerReader(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CashBalance cashBalance(UserId userId) {
        return jdbc.sql(CASH_BALANCE_SQL).param("userId", userId.value())
                .query((rs, rowNum) -> new CashBalance(
                        Money.of(rs.getBigDecimal("cash")), Money.of(rs.getBigDecimal("reserved"))))
                .single();
    }

    @Override
    public ShareBalance shareBalance(UserId userId, InstrumentId instrumentId) {
        return jdbc.sql(SHARE_BALANCE_SQL)
                .param("userId", userId.value()).param("instrumentId", instrumentId.value())
                .query((rs, rowNum) -> new ShareBalance(
                        rs.getBigDecimal("quantity").stripTrailingZeros().longValueExact(),
                        rs.getBigDecimal("reserved").stripTrailingZeros().longValueExact()))
                .single();
    }
}
