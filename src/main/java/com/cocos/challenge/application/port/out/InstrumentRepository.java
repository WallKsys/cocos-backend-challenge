package com.cocos.challenge.application.port.out;

import com.cocos.challenge.domain.model.Instrument;
import com.cocos.challenge.domain.model.InstrumentId;
import com.cocos.challenge.domain.model.InstrumentSearchCriteria;
import com.cocos.challenge.domain.model.InstrumentSummary;

import java.util.List;
import java.util.Optional;

/** The instruments table: lookup, the ARS cash instrument and the ranked search (spec 004). */
public interface InstrumentRepository {

    Optional<Instrument> findById(InstrumentId instrumentId);

    Optional<InstrumentId> findCashInstrument();

    List<RankedInstrument> search(InstrumentSearchCriteria criteria, int maxResults);

    record RankedInstrument(int rank, InstrumentSummary instrument) {
    }
}
