package com.cocos.challenge.infrastructure.adapter.in.rest.instrument;

import com.cocos.challenge.application.port.in.SearchInstrumentsQuery;
import com.cocos.challenge.domain.model.InstrumentPage;
import com.cocos.challenge.domain.model.InstrumentSearchCriteria;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InstrumentSearchController {

    private final SearchInstrumentsQuery query;

    public InstrumentSearchController(SearchInstrumentsQuery query) {
        this.query = query;
    }

    @GetMapping("/api/v1/instruments")
    public InstrumentPageResponse search(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "ticker", required = false) String ticker,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "cursor", required = false) String cursor) {
        InstrumentPage page = query.search(InstrumentSearchCriteria.of(q, ticker, name, limit, cursor));
        return InstrumentPageResponse.from(page);
    }
}
