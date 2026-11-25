package com.cocos.challenge.application.port.out;

import com.cocos.challenge.domain.model.UserId;

import java.util.Optional;

public interface AdminOrderRepository {

    Optional<UserId> findOwner(long orderId);

    boolean delete(long orderId);

    boolean executeIfNew(long orderId);
}
