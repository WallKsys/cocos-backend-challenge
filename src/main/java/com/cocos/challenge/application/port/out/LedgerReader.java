package com.cocos.challenge.application.port.out;

import com.cocos.challenge.domain.model.CashBalance;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.ShareBalance;
import com.cocos.challenge.domain.model.UserId;

public interface LedgerReader {

    CashBalance cashBalance(UserId userId);

    ShareBalance shareBalance(UserId userId, InstrumentId instrumentId);
}
