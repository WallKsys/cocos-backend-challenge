package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.AdminOrderRepository;
import com.cocos.challenge.application.port.out.OrderLedgerRepository;
import com.cocos.challenge.domain.model.CashMovement;
import com.cocos.challenge.domain.model.HistoryCursor;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.domain.model.Order;
import com.cocos.challenge.domain.model.OrderHistoryCriteria;
import com.cocos.challenge.domain.model.OrderRecord;
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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

public class JdbcOrderLedgerRepository implements OrderLedgerRepository, AdminOrderRepository {

    static final String FIND_IDEMPOTENCY_RECORD_SQL = """
            SELECT id, side, request_fingerprint FROM orders WHERE userid = :userId AND idempotency_key = :key
            """;

    static final String FIND_ORDER_SQL = """
            SELECT o.id, o.userid, o.instrumentid, i.ticker, o.side, o.type, o.status, o.size, o.price,
                   o.requested_amount, o.rejection_code, o.rejection_message, o.datetime,
                   o.idempotency_key, o.request_fingerprint
            FROM orders o JOIN instruments i ON i.id = o.instrumentid
            WHERE o.id = :id
            """;

    static final String INSERT_ORDER_SQL = """
            INSERT INTO orders (instrumentid, userid, size, price, type, side, status, datetime,
                                rejection_code, rejection_message, idempotency_key, request_fingerprint,
                                requested_amount)
            VALUES (:instrumentId, :userId, :size, :price, :type, :side, :status, :createdAt,
                    :rejectionCode, :rejectionMessage, :key, :fingerprint, :requestedAmount)
            RETURNING id
            """;

    static final String FIND_CASH_MOVEMENT_SQL = """
            SELECT id, userid, side, status, size, rejection_code, rejection_message, datetime,
                   idempotency_key, request_fingerprint
            FROM orders
            WHERE id = :id AND side IN ('CASH_IN', 'CASH_OUT')
            """;

    static final String INSERT_CASH_MOVEMENT_SQL = """
            INSERT INTO orders (instrumentid, userid, size, price, type, side, status, datetime,
                                rejection_code, rejection_message, idempotency_key, request_fingerprint)
            VALUES (:instrumentId, :userId, :amount, 1, 'MARKET', :side, :status, :createdAt,
                    :rejectionCode, :rejectionMessage, :key, :fingerprint)
            RETURNING id
            """;

    static final String FIND_OWNER_SQL = """
            SELECT userid FROM orders WHERE id = :orderId
            """;

    static final String DELETE_SQL = """
            DELETE FROM orders WHERE id = :orderId
            """;

    static final String EXECUTE_IF_NEW_SQL = """
            UPDATE orders SET status = 'FILLED' WHERE id = :orderId AND status = 'NEW'
            """;

    private static final String SELECT_RECORD = """
            SELECT o.id, o.instrumentid, i.ticker, o.side, o.type, o.status, o.size, o.price, o.requested_amount,
                   o.rejection_code, o.rejection_message, o.datetime
            FROM orders o
            JOIN instruments i ON i.id = o.instrumentid
            """;

    static final String FIND_RECORD_SQL = SELECT_RECORD + """
            WHERE o.id = :orderId AND o.userid = :userId
            """;

    static final String FIND_RECORD_BY_ID_SQL = SELECT_RECORD + """
            WHERE o.id = :orderId
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
    public Optional<IdempotencyRecord> findIdempotencyRecord(UserId userId, String key) {
        return jdbc.sql(FIND_IDEMPOTENCY_RECORD_SQL).param("userId", userId.value()).param("key", key)
                .query((rs, rowNum) -> new IdempotencyRecord(rs.getLong("id"),
                        OrderSide.valueOf(rs.getString("side")),
                        new RequestFingerprint(rs.getString("request_fingerprint"))))
                .optional();
    }

    @Override
    public Order findById(long id) {
        return jdbc.sql(FIND_ORDER_SQL).param("id", id)
                .query((rs, rowNum) -> mapOrder(rs)).single();
    }

    @Override
    public Order save(NewOrder order) {
        Rejection rejection = order.rejection();
        long id = jdbc.sql(INSERT_ORDER_SQL)
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

    @Override
    public CashMovement findCashMovement(long id) {
        return jdbc.sql(FIND_CASH_MOVEMENT_SQL).param("id", id)
                .query((rs, rowNum) -> mapMovement(rs)).single();
    }

    @Override
    public CashMovement save(NewCashMovement movement) {
        Rejection rejection = movement.rejection();
        long id = jdbc.sql(INSERT_CASH_MOVEMENT_SQL)
                .param("instrumentId", movement.instrumentId().value())
                .param("userId", movement.userId().value())
                .param("amount", movement.amount().amount())
                .param("side", movement.side().name())
                .param("status", movement.status().name())
                .param("createdAt", movement.createdAt().atOffset(ZoneOffset.UTC))
                .param("rejectionCode", rejection == null ? null : rejection.reason().name())
                .param("rejectionMessage", rejection == null ? null : rejection.message())
                .param("key", movement.idempotencyKey())
                .param("fingerprint", movement.fingerprint().hex())
                .query(Long.class).single();
        return findCashMovement(id);
    }

    @Override
    public Optional<UserId> findOwner(long orderId) {
        return jdbc.sql(FIND_OWNER_SQL)
                .param("orderId", orderId)
                .query((rs, rowNum) -> new UserId(rs.getLong("userid")))
                .optional();
    }

    @Override
    public boolean delete(long orderId) {
        return jdbc.sql(DELETE_SQL).param("orderId", orderId).update() == 1;
    }

    @Override
    public boolean executeIfNew(long orderId) {
        return jdbc.sql(EXECUTE_IF_NEW_SQL).param("orderId", orderId).update() == 1;
    }

    @Override
    public Optional<OrderRecord> findRecord(UserId userId, long orderId) {
        return jdbc.sql(FIND_RECORD_SQL)
                .param("orderId", orderId)
                .param("userId", userId.value())
                .query((rs, rowNum) -> mapRecord(rs)).optional();
    }

    @Override
    public Optional<OrderRecord> findRecordById(long orderId) {
        return jdbc.sql(FIND_RECORD_BY_ID_SQL)
                .param("orderId", orderId)
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
        return new OrderRecord(
                rs.getLong("id"), new InstrumentId(rs.getLong("instrumentid")), rs.getString("ticker"),
                OrderSide.valueOf(rs.getString("side")), OrderType.valueOf(rs.getString("type")),
                OrderStatus.valueOf(rs.getString("status")), rs.getBigDecimal("size"),
                money(rs.getBigDecimal("price")), money(rs.getBigDecimal("requested_amount")),
                rejection(rs), createdAt(rs));
    }

    private static Order mapOrder(ResultSet rs) throws SQLException {
        return new Order(rs.getLong("id"), new UserId(rs.getLong("userid")),
                new InstrumentId(rs.getLong("instrumentid")), rs.getString("ticker"),
                OrderSide.valueOf(rs.getString("side")), OrderType.valueOf(rs.getString("type")),
                OrderStatus.valueOf(rs.getString("status")), rs.getBigDecimal("size").longValueExact(),
                money(rs.getBigDecimal("price")), money(rs.getBigDecimal("requested_amount")),
                rejection(rs), createdAt(rs), rs.getString("idempotency_key"), fingerprint(rs));
    }

    private static CashMovement mapMovement(ResultSet rs) throws SQLException {
        return new CashMovement(rs.getLong("id"), new UserId(rs.getLong("userid")),
                OrderSide.valueOf(rs.getString("side")), OrderStatus.valueOf(rs.getString("status")),
                Money.of(rs.getBigDecimal("size")), rejection(rs),
                createdAt(rs), rs.getString("idempotency_key"), fingerprint(rs));
    }

    private static Rejection rejection(ResultSet rs) throws SQLException {
        String code = rs.getString("rejection_code");
        return code == null ? null : new Rejection(RejectionReason.valueOf(code), rs.getString("rejection_message"));
    }

    private static BigDecimal amount(Money money) {
        return money == null ? null : money.amount();
    }

    private static Money money(BigDecimal amount) {
        return amount == null ? null : Money.of(amount);
    }

    private static Instant createdAt(ResultSet rs) throws SQLException {
        return rs.getObject("datetime", OffsetDateTime.class).toInstant();
    }

    private static RequestFingerprint fingerprint(ResultSet rs) throws SQLException {
        String fingerprint = rs.getString("request_fingerprint");
        return fingerprint == null ? null : new RequestFingerprint(fingerprint);
    }
}
