package com.cocos.challenge.application.port.in;

import com.cocos.challenge.domain.model.Portfolio;
import com.cocos.challenge.domain.model.UserId;

public interface GetPortfolioQuery {

    Portfolio get(UserId userId);
}
