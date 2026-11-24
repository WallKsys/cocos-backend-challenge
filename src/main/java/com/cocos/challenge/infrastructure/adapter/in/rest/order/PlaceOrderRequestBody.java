package com.cocos.challenge.infrastructure.adapter.in.rest.order;

import com.cocos.challenge.infrastructure.adapter.in.rest.format.DecimalString;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PlaceOrderRequestBody(
        @NotNull @Positive Long instrumentId,
        @NotNull String side,
        @NotNull String type,
        Integer quantity,
        @DecimalString String amount,
        @DecimalString String price) {
}
