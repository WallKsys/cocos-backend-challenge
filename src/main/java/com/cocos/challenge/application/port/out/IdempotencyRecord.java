package com.cocos.challenge.application.port.out;

import com.cocos.challenge.domain.model.OrderSide;
import com.cocos.challenge.domain.model.RequestFingerprint;

public record IdempotencyRecord(long orderId, OrderSide side, RequestFingerprint fingerprint) {
}
