package com.cocos.challenge.application.port.out;

import com.cocos.challenge.domain.model.CashBalance;
import com.cocos.challenge.domain.model.Fill;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.ShareBalance;
import com.cocos.challenge.domain.model.UserId;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Aggregations over the orders ledger, computed in SQL (TEC-17). */
public interface LedgerReader {

    CashBalance cashBalance(UserId userId);

    ShareBalance shareBalance(UserId userId, InstrumentId instrumentId);

    List<OpenPosition> openPositions(UserId userId);

    Map<InstrumentId, List<Fill>> fills(UserId userId, Set<InstrumentId> instrumentIds);

    record OpenPosition(InstrumentId id, String ticker, String name, long quantity, long reservedQuantity) {
    }
}
