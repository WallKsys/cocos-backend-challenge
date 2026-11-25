package com.cocos.challenge.application.port.in;

import com.cocos.challenge.domain.model.OrderRecord;

public interface ExecuteOrderUseCase {

    OrderRecord execute(long orderId);
}
