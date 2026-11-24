package com.cocos.challenge.infrastructure.adapter.in.rest.portfolio;

import com.cocos.challenge.domain.model.CashBalance;
import com.cocos.challenge.infrastructure.adapter.in.rest.format.ApiDecimals;

record CashResponse(String total, String reserved, String available) {

    static CashResponse from(CashBalance cash) {
        return new CashResponse(ApiDecimals.format(cash.cash()), ApiDecimals.format(cash.reserved()),
                ApiDecimals.format(cash.available()));
    }
}
