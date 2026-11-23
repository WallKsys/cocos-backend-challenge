package com.cocos.challenge.application.port.out;

import com.cocos.challenge.domain.model.Order;
import com.cocos.challenge.domain.model.UserId;

import java.util.Optional;

public interface OrderRepository {

    Optional<IdempotencyRecord> findIdempotencyRecord(UserId userId, String key);

    Order findById(long id);

    Order save(NewOrder order);
}
