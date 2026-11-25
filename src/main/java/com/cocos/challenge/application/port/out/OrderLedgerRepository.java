package com.cocos.challenge.application.port.out;

import com.cocos.challenge.domain.model.OrderHistoryCriteria;
import com.cocos.challenge.domain.model.OrderRecord;
import com.cocos.challenge.domain.model.UserId;

import java.util.List;
import java.util.Optional;

public interface OrderLedgerRepository {

    Optional<OrderRecord> findRecord(UserId userId, long orderId);

    Optional<OrderRecord> findRecordById(long orderId);

    boolean cancelIfNew(UserId userId, long orderId);

    List<OrderRecord> history(UserId userId, OrderHistoryCriteria criteria, int maxResults);
}
