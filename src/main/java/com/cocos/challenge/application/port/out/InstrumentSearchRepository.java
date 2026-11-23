package com.cocos.challenge.application.port.out;

import com.cocos.challenge.domain.model.InstrumentSearchCriteria;
import com.cocos.challenge.domain.model.RankedInstrument;

import java.util.List;

public interface InstrumentSearchRepository {
    List<RankedInstrument> search(InstrumentSearchCriteria criteria, int maxResults);
}
