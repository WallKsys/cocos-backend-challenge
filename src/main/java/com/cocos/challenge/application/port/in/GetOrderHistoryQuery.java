package com.cocos.challenge.application.port.in;

import com.cocos.challenge.domain.model.CursorPage;
import com.cocos.challenge.domain.model.HistoryCursor;
import com.cocos.challenge.domain.model.OrderHistoryCriteria;
import com.cocos.challenge.domain.model.OrderRecord;
import com.cocos.challenge.domain.model.UserId;

public interface GetOrderHistoryQuery {

    CursorPage<OrderRecord, HistoryCursor> list(UserId userId, OrderHistoryCriteria criteria);
}
