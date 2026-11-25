package com.cocos.challenge.application.port.in;

import com.cocos.challenge.domain.model.UserId;

public interface CancelOrderUseCase {

    CancellationResult cancel(UserId userId, long orderId);
}
