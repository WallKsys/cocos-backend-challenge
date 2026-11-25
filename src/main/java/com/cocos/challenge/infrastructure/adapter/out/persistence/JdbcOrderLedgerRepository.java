package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.domain.model.HistoryCursor;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.domain.model.OrderHistoryCriteria;
import com.cocos.challenge.domain.model.OrderRecord;
import com.cocos.challenge.domain.model.OrderSide;
import com.cocos.challenge.domain.model.OrderStatus;
import com.cocos.challenge.domain.model.OrderType;
import com.cocos.challenge.domain.model.Rejection;
import com.cocos.challenge.domain.model.RejectionReason;
import com.cocos.challenge.domain.model.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

public class JdbcOrderLedgerRepository implements OrderLedgerRepository {

    private static final String SELECT_RECORD = """
            SELECT o.id, o.instrumentid, i.ticker, o.side, o.type, o.status, o.size, o.price, o.requested_amount,
                   o.rejection_code, o.rejection_message, o.datetime
            FROM orders o
            JOIN instruments i ON i.id = o.instrumentid
            """;

    static final String FIND_RECORD_SQL = SELECT_RECORD + """
            WHERE o.id = :orderId AND o.userid = :userId
            """;

    static final String CANCEL_IF_NEW_SQL = """
            UPDATE orders SET status = 'CANCELLED'
            WHERE id = :orderId AND userid = :userId AND status = 'NEW'
            """;

    private static final String HISTORY_USER = """
            WHERE o.userid = :userId
            """;

    private static final String HISTORY_STATUS = """
              AND o.status = :status
            """;

    private static final String HISTORY_CURSOR = """
              AND (o.datetime, o.id) < (:cursorCreatedAt, :cursorOrderId)
            """;

    private static final String HISTORY_ORDER = """
            ORDER BY o.datetime DESC, o.id DESC
            LIMIT :maxResults
            """;

    private final JdbcClient jdbc;

    public JdbcOrderLedgerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<OrderRecord> findRecord(UserId userId, long orderId) {
        return jdbc.sql(FIND_RECORD_SQL)
                .param("orderId", orderId)
                .param("userId", userId.value())
                .query((rs, rowNum) -> mapRecord(rs)).optional();
    }

    @Override
    public boolean cancelIfNew(UserId userId, long orderId) {
        return jdbc.sql(CANCEL_IF_NEW_SQL)
                .param("orderId", orderId)
                .param("userId", userId.value())
                .update() == 1;
    }

    @Override
    public List<OrderRecord> history(UserId userId, OrderHistoryCriteria criteria, int maxResults) {
        var statement = jdbc.sql(historySql(criteria))
                .param("userId", userId.value())
                .param("maxResults", maxResults);
        if (criteria.status() != null) {
            statement.param("status", criteria.status().name());
        }
        HistoryCursor cursor = criteria.cursor();
        if (cursor != null) {
            statement.param("cursorCreatedAt", cursor.createdAt().atOffset(ZoneOffset.UTC))
                    .param("cursorOrderId", cursor.orderId());
        }
        return statement.query((rs, rowNum) -> mapRecord(rs)).list();
    }

    String historySql(OrderHistoryCriteria criteria) {
        return SELECT_RECORD + HISTORY_USER
                + (criteria.status() == null ? "" : HISTORY_STATUS)
                + (criteria.cursor() == null ? "" : HISTORY_CURSOR)
                + HISTORY_ORDER;
    }

    private static OrderRecord mapRecord(ResultSet rs) throws SQLException {
        String rejectionCode = rs.getString("rejection_code");
        Rejection rejection = rejectionCode == null ? null
                : new Rejection(RejectionReason.valueOf(rejectionCode), rs.getString("rejection_message"));
        BigDecimal price = rs.getBigDecimal("price");
        BigDecimal requestedAmount = rs.getBigDecimal("requested_amount");
        return new OrderRecord(
                rs.getLong("id"), new InstrumentId(rs.getLong("instrumentid")), rs.getString("ticker"),
                OrderSide.valueOf(rs.getString("side")), OrderType.valueOf(rs.getString("type")),
                OrderStatus.valueOf(rs.getString("status")), rs.getBigDecimal("size"),
                price == null ? null : Money.of(price),
                requestedAmount == null ? null : Money.of(requestedAmount), rejection,
                rs.getObject("datetime", OffsetDateTime.class).toInstant());
    }
}
