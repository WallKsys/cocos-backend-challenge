package com.cocos.challenge.application.port.in;

import com.cocos.challenge.domain.model.InstrumentPage;
import com.cocos.challenge.domain.model.InstrumentSearchCriteria;

public interface SearchInstrumentsQuery {
    InstrumentPage search(InstrumentSearchCriteria criteria);
}
