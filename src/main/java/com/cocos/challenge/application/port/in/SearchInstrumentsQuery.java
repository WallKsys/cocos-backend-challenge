package com.cocos.challenge.application.port.in;

import com.cocos.challenge.domain.model.CursorPage;
import com.cocos.challenge.domain.model.InstrumentSearchCriteria;
import com.cocos.challenge.domain.model.InstrumentSummary;
import com.cocos.challenge.domain.model.SearchCursor;

public interface SearchInstrumentsQuery {
    CursorPage<InstrumentSummary, SearchCursor> search(InstrumentSearchCriteria criteria);
}
