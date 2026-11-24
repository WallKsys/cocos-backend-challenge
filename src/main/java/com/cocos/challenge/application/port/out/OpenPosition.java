package com.cocos.challenge.application.port.out;

import com.cocos.challenge.domain.model.InstrumentId;

public record OpenPosition(InstrumentId id, String ticker, String name, long quantity, long reservedQuantity) {
}
