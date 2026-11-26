package com.cocos.challenge.application.port.out;

import com.cocos.challenge.domain.model.CashMovement;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.Money;
import com.cocos.challenge.domain.model.Order;
import com.cocos.challenge.domain.model.OrderHistoryCriteria;
import com.cocos.challenge.domain.model.OrderRecord;
import com.cocos.challenge.domain.model.OrderSide;
import com.cocos.challenge.domain.model.OrderStatus;
import com.cocos.challenge.domain.model.OrderType;
import com.cocos.challenge.domain.model.Rejection;
import com.cocos.challenge.domain.model.RequestFingerprint;
import com.cocos.challenge.domain.model.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Rows of the orders table: orders, cash movements and their records (Constitution I). */
public interface OrderLedgerRepository {

    Optional<IdempotencyRecord> findIdempotencyRecord(UserId userId, String key);

    Order findById(long id);

    Order save(NewOrder order);

    CashMovement findCashMovement(long id);

    CashMovement save(NewCashMovement movement);

    Optional<OrderRecord> findRecord(UserId userId, long orderId);

    Optional<OrderRecord> findRecordById(long orderId);

    boolean cancelIfNew(UserId userId, long orderId);

    List<OrderRecord> history(UserId userId, OrderHistoryCriteria criteria, int maxResults);

    record IdempotencyRecord(long orderId, OrderSide side, RequestFingerprint fingerprint) {
    }

    record NewOrder(
            UserId userId, InstrumentId instrumentId, String ticker, OrderSide side, OrderType type,
            OrderStatus status, long quantity, Money price, Money requestedAmount, Rejection rejection,
            Instant createdAt, String idempotencyKey, RequestFingerprint fingerprint) {
    }

    record NewCashMovement(UserId userId, InstrumentId instrumentId, OrderSide side, OrderStatus status,
            Money amount, Rejection rejection, Instant createdAt, String idempotencyKey,
            RequestFingerprint fingerprint) {
    }
}
