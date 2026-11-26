package com.cocos.challenge.infrastructure.adapter.in.rest.order;

import com.cocos.challenge.application.port.in.CancelOrderUseCase.CancellationResult;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

public record CancellationResponse(@JsonUnwrapped OrderRecordResponse order, boolean alreadyCancelled) {

    static CancellationResponse from(CancellationResult result) {
        return new CancellationResponse(OrderRecordResponse.from(result.order()), result.alreadyCancelled());
    }
}
