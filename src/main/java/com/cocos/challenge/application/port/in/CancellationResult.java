package com.cocos.challenge.application.port.in;

import com.cocos.challenge.domain.model.OrderRecord;

public record CancellationResult(OrderRecord order, boolean alreadyCancelled) {
}
