package com.cocos.challenge.infrastructure.adapter.in.rest.order;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

public record CancellationResponse(@JsonUnwrapped OrderRecordResponse order, boolean alreadyCancelled) {
}
