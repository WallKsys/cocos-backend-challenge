package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.IdempotencyRecord;
import com.cocos.challenge.application.port.out.NewOrder;
import com.cocos.challenge.application.port.out.OrderRepository;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.domain.model.Order;
import com.cocos.challenge.domain.model.OrderSide;
import com.cocos.challenge.domain.model.OrderStatus;
import com.cocos.challenge.domain.model.OrderType;
import com.cocos.challenge.domain.model.Rejection;
import com.cocos.challenge.domain.model.RejectionReason;
import com.cocos.challenge.domain.model.RequestFingerprint;
import com.cocos.challenge.domain.model.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

public class JdbcOrderRepository implements OrderRepository {

    static final String FIND_IDEMPOTENCY_RECORD_SQL = """
            SELECT id, side, request_fingerprint FROM orders WHERE userid = :userId AND idempotency_key = :key
            """;

    static final String FIND_BY_ID_SQL = """
            SELECT o.id, o.userid, o.instrumentid, i.ticker, o.side, o.type, o.status, o.size, o.price,
                   o.requested_amount, o.rejection_code, o.rejection_message, o.datetime,
                   o.idempotency_key, o.request_fingerprint
            FROM orders o JOIN instruments i ON i.id = o.instrumentid
            WHERE o.id = :id
            """;

    static final String INSERT_SQL = """
            INSERT INTO orders (instrumentid, userid, size, price, type, side, status, datetime,
                                rejection_code, rejection_message, idempotency_key, request_fingerprint,
                                requested_amount)
            VALUES (:instrumentId, :userId, :size, :price, :type, :side, :status, :createdAt,
                    :rejectionCode, :rejectionMessage, :key, :fingerprint, :requestedAmount)
            RETURNING id
            """;

    private final JdbcClient jdbc;

    public JdbcOrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<IdempotencyRecord> findIdempotencyRecord(UserId userId, String key) {
        return jdbc.sql(FIND_IDEMPOTENCY_RECORD_SQL).param("userId", userId.value()).param("key", key)
                .query((rs, rowNum) -> new IdempotencyRecord(rs.getLong("id"),
                        OrderSide.valueOf(rs.getString("side")),
                        new RequestFingerprint(rs.getString("request_fingerprint"))))
                .optional();
    }

    @Override
    public Order findById(long id) {
        return jdbc.sql(FIND_BY_ID_SQL).param("id", id)
                .query((rs, rowNum) -> mapOrder(rs)).single();
    }

    @Override
    public Order save(NewOrder order) {
        Rejection rejection = order.rejection();
        long id = jdbc.sql(INSERT_SQL)
                .param("instrumentId", order.instrumentId().value())
                .param("userId", order.userId().value())
                .param("size", BigDecimal.valueOf(order.quantity()))
                .param("price", amount(order.price()))
                .param("type", order.type().name())
                .param("side", order.side().name())
                .param("status", order.status().name())
                .param("createdAt", order.createdAt().atOffset(ZoneOffset.UTC))
                .param("rejectionCode", rejection == null ? null : rejection.reason().name())
                .param("rejectionMessage", rejection == null ? null : rejection.message())
                .param("key", order.idempotencyKey())
                .param("fingerprint", order.fingerprint() == null ? null : order.fingerprint().hex())
                .param("requestedAmount", amount(order.requestedAmount()))
                .query(Long.class).single();
        return findById(id);
    }

    private static Order mapOrder(ResultSet rs) throws SQLException {
        String reason = rs.getString("rejection_code");
        Rejection rejection = reason == null ? null
                : new Rejection(RejectionReason.valueOf(reason), rs.getString("rejection_message"));
        String fingerprint = rs.getString("request_fingerprint");
        return new Order(rs.getLong("id"), new UserId(rs.getLong("userid")),
                new InstrumentId(rs.getLong("instrumentid")), rs.getString("ticker"),
                OrderSide.valueOf(rs.getString("side")), OrderType.valueOf(rs.getString("type")),
                OrderStatus.valueOf(rs.getString("status")), rs.getBigDecimal("size").longValueExact(),
                money(rs.getBigDecimal("price")), money(rs.getBigDecimal("requested_amount")), rejection,
                rs.getObject("datetime", OffsetDateTime.class).toInstant(), rs.getString("idempotency_key"),
                fingerprint == null ? null : new RequestFingerprint(fingerprint));
    }

    private static BigDecimal amount(Money money) {
        return money == null ? null : money.amount();
    }

    private static Money money(BigDecimal amount) {
        return amount == null ? null : Money.of(amount);
    }
}
