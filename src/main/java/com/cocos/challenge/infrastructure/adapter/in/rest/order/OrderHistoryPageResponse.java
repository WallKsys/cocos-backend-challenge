package com.cocos.challenge.infrastructure.adapter.in.rest.order;

import java.util.List;

public record OrderHistoryPageResponse(List<OrderRecordResponse> items, String nextCursor) {
}
