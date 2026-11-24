package com.cocos.challenge.application.port.out;

import com.cocos.challenge.domain.model.Fill;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.UserId;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface PortfolioReader {

    List<OpenPosition> openPositions(UserId userId);

    Map<InstrumentId, List<Fill>> fills(UserId userId, Set<InstrumentId> instrumentIds);
}
