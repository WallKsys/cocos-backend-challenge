package com.cocos.challenge.application.port.in;

import com.cocos.challenge.domain.model.OrderRecord;
import com.cocos.challenge.domain.model.UserId;

public interface GetOrderQuery {

    OrderRecord get(UserId userId, long orderId);
}
