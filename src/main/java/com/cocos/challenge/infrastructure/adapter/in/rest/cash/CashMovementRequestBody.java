package com.cocos.challenge.infrastructure.adapter.in.rest.cash;

import com.cocos.challenge.infrastructure.adapter.in.rest.format.DecimalString;
import jakarta.validation.constraints.NotNull;

public record CashMovementRequestBody(@NotNull @DecimalString String amount) {
}
