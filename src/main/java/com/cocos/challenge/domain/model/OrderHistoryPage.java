package com.cocos.challenge.domain.model;

import java.util.List;

public record OrderHistoryPage(List<OrderRecord> items, HistoryCursor nextCursor) {

    public OrderHistoryPage {
        items = List.copyOf(items);
    }
}
