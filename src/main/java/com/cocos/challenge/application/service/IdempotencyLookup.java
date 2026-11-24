package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.out.BusinessMetrics;
import com.cocos.challenge.application.port.out.OrderRepository;
import com.cocos.challenge.domain.model.RequestFingerprint;
import com.cocos.challenge.domain.model.UserId;

import java.util.OptionalLong;

public final class IdempotencyLookup {

    public IdempotencyLookup(OrderRepository orders, BusinessMetrics metrics) {
    }

    public OptionalLong find(UserId userId, String key, RequestFingerprint fingerprint) {
        throw new UnsupportedOperationException();
    }
}
