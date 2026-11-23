package com.cocos.challenge.application.port.out;

import com.cocos.challenge.domain.model.Instrument;
import com.cocos.challenge.domain.model.InstrumentId;

import java.util.Optional;

public interface InstrumentRepository {

    Optional<Instrument> findById(InstrumentId instrumentId);
}
