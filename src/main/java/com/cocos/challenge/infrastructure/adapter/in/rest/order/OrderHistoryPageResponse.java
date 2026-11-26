package com.cocos.challenge.infrastructure.adapter.in.rest.order;

import com.cocos.challenge.domain.model.CursorPage;
import com.cocos.challenge.domain.model.HistoryCursor;
import com.cocos.challenge.domain.model.OrderRecord;

import java.util.List;

public record OrderHistoryPageResponse(List<OrderRecordResponse> items, String nextCursor) {

    public static OrderHistoryPageResponse from(CursorPage<OrderRecord, HistoryCursor> page) {
        return new OrderHistoryPageResponse(page.items().stream().map(OrderRecordResponse::from).toList(),
                page.nextCursor() == null ? null : page.nextCursor().encode());
    }
}
