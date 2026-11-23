package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.SearchInstrumentsQuery;
import com.cocos.challenge.application.port.out.InstrumentSearchRepository;
import com.cocos.challenge.domain.model.InstrumentPage;
import com.cocos.challenge.domain.model.InstrumentSearchCriteria;
import org.springframework.transaction.annotation.Transactional;

public class SearchInstrumentsService implements SearchInstrumentsQuery {

    private final InstrumentSearchRepository repository;

    public SearchInstrumentsService(InstrumentSearchRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public InstrumentPage search(InstrumentSearchCriteria criteria) {
        throw new UnsupportedOperationException();
    }
}
