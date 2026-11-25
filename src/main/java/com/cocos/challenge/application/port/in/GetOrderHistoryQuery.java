package com.cocos.challenge.application.port.in;

import com.cocos.challenge.domain.model.OrderHistoryCriteria;
import com.cocos.challenge.domain.model.OrderHistoryPage;
import com.cocos.challenge.domain.model.UserId;

public interface GetOrderHistoryQuery {

    OrderHistoryPage list(UserId userId, OrderHistoryCriteria criteria);
}
