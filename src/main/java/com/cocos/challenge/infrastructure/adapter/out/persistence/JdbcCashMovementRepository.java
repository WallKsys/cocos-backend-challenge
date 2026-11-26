package com.cocos.challenge.infrastructure.adapter.out.persistence;

import com.cocos.challenge.application.port.out.CashMovementRepository;
import com.cocos.challenge.application.port.out.NewCashMovement;
import com.cocos.challenge.domain.model.CashMovement;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.domain.model.OrderSide;
import com.cocos.challenge.domain.model.OrderStatus;
import com.cocos.challenge.domain.model.Rejection;
import com.cocos.challenge.domain.model.RejectionReason;
import com.cocos.challenge.domain.model.RequestFingerprint;
import com.cocos.challenge.domain.model.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class JdbcCashMovementRepository implements CashMovementRepository {

    static final String FIND_BY_ID_SQL = """
            SELECT id, userid, side, status, size, rejection_code, rejection_message, datetime,
                   idempotency_key, request_fingerprint
            FROM orders
            WHERE id = :id AND side IN ('CASH_IN', 'CASH_OUT')
            """;

    static final String INSERT_SQL = """
            INSERT INTO orders (instrumentid, userid, size, price, type, side, status, datetime,
                                rejection_code, rejection_message, idempotency_key, request_fingerprint)
            VALUES (:instrumentId, :userId, :amount, 1, 'MARKET', :side, :status, :createdAt,
                    :rejectionCode, :rejectionMessage, :key, :fingerprint)
            RETURNING id
            """;

    private final JdbcClient jdbc;

    public JdbcCashMovementRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CashMovement findById(long id) {
        return jdbc.sql(FIND_BY_ID_SQL).param("id", id)
                .query((rs, rowNum) -> mapMovement(rs)).single();
    }

    @Override
    public CashMovement save(NewCashMovement movement) {
        Rejection rejection = movement.rejection();
        long id = jdbc.sql(INSERT_SQL)
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
        return findById(id);
    }

    private static CashMovement mapMovement(ResultSet rs) throws SQLException {
        String reason = rs.getString("rejection_code");
        Rejection rejection = reason == null ? null
                : new Rejection(RejectionReason.valueOf(reason), rs.getString("rejection_message"));
        String fingerprint = rs.getString("request_fingerprint");
        return new CashMovement(rs.getLong("id"), new UserId(rs.getLong("userid")),
                OrderSide.valueOf(rs.getString("side")), OrderStatus.valueOf(rs.getString("status")),
                Money.of(rs.getBigDecimal("size")), rejection,
                rs.getObject("datetime", OffsetDateTime.class).toInstant(), rs.getString("idempotency_key"),
                fingerprint == null ? null : new RequestFingerprint(fingerprint));
    }
}
