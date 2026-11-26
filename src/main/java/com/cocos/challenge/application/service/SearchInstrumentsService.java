package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.SearchInstrumentsQuery;
import com.cocos.challenge.application.port.out.InstrumentRepository;
import com.cocos.challenge.application.port.out.InstrumentRepository.RankedInstrument;
import com.cocos.challenge.domain.model.CursorPage;
import com.cocos.challenge.domain.model.InstrumentSearchCriteria;
import com.cocos.challenge.domain.model.InstrumentSummary;
import com.cocos.challenge.domain.model.SearchCursor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public class SearchInstrumentsService implements SearchInstrumentsQuery {

    private final InstrumentRepository repository;

    public SearchInstrumentsService(InstrumentRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<InstrumentSummary, SearchCursor> search(InstrumentSearchCriteria criteria) {
        List<RankedInstrument> results = repository.search(criteria, criteria.limit() + 1);
        List<RankedInstrument> page = results.stream().limit(criteria.limit()).toList();
        SearchCursor nextCursor = null;
        if (results.size() > criteria.limit()) {
            RankedInstrument last = page.getLast();
            nextCursor = new SearchCursor(last.rank(), last.instrument().ticker());
        }
        return new CursorPage<>(page.stream().map(RankedInstrument::instrument).toList(), nextCursor);
    }
}
