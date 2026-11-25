package com.cocos.challenge.infrastructure.adapter.in.rest.internal;

import com.cocos.challenge.infrastructure.adapter.in.rest.format.DecimalString;
import jakarta.validation.constraints.NotNull;

public record MarketDataUpdateBody(@NotNull @DecimalString String close, @DecimalString String previousClose) {
}
