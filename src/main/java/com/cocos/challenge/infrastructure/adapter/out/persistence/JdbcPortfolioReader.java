package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.OpenPosition;
import com.cocos.challenge.application.port.out.PortfolioReader;
import com.cocos.challenge.domain.model.Fill;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.domain.model.OrderSide;
import com.cocos.challenge.domain.model.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JdbcPortfolioReader implements PortfolioReader {

    static final String OPEN_POSITIONS_SQL = """
            SELECT o.instrumentid, i.ticker, i.name,
                   SUM(CASE WHEN o.status = 'FILLED' AND o.side = 'BUY'  THEN o.size
                            WHEN o.status = 'FILLED' AND o.side = 'SELL' THEN -o.size ELSE 0 END) AS quantity,
                   SUM(CASE WHEN o.status = 'NEW' AND o.side = 'SELL' THEN o.size ELSE 0 END) AS reserved
            FROM orders o
            JOIN instruments i ON i.id = o.instrumentid
            WHERE o.userid = :userId AND o.status IN ('FILLED', 'NEW') AND o.side IN ('BUY', 'SELL')
            GROUP BY o.instrumentid, i.ticker, i.name
            HAVING SUM(CASE WHEN o.status = 'FILLED' AND o.side = 'BUY'  THEN o.size
                            WHEN o.status = 'FILLED' AND o.side = 'SELL' THEN -o.size ELSE 0 END) <> 0
            ORDER BY i.ticker COLLATE "C"
            """;

    static final String FILLS_SQL = """
            SELECT instrumentid, side, size, price
            FROM orders
            WHERE userid = :userId AND status = 'FILLED' AND side IN ('BUY', 'SELL') AND instrumentid IN (:instrumentIds)
            ORDER BY instrumentid, datetime, id
            """;

    private final JdbcClient jdbc;

    public JdbcPortfolioReader(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<OpenPosition> openPositions(UserId userId) {
        return jdbc.sql(OPEN_POSITIONS_SQL).param("userId", userId.value())
                .query((rs, rowNum) -> new OpenPosition(new InstrumentId(rs.getLong("instrumentid")),
                        rs.getString("ticker"), rs.getString("name"), rs.getLong("quantity"), rs.getLong("reserved")))
                .list();
    }

    @Override
    public Map<InstrumentId, List<Fill>> fills(UserId userId, Set<InstrumentId> instrumentIds) {
        if (instrumentIds.isEmpty()) {
            return Map.of();
        }
        Map<InstrumentId, List<Fill>> fills = new HashMap<>();
        jdbc.sql(FILLS_SQL).param("userId", userId.value())
                .param("instrumentIds", instrumentIds.stream().map(InstrumentId::value).toList())
                .query(rs -> {
                    InstrumentId id = new InstrumentId(rs.getLong("instrumentid"));
                    Fill fill = new Fill(OrderSide.valueOf(rs.getString("side")), rs.getLong("size"),
                            Money.of(rs.getBigDecimal("price")));
                    fills.computeIfAbsent(id, ignored -> new ArrayList<>()).add(fill);
                });
        return fills;
    }
}
