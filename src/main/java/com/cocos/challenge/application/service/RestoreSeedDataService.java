package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.RestoreSeedDataUseCase;
import com.cocos.challenge.application.port.out.SeedDataRestorer;
import org.springframework.transaction.annotation.Transactional;

public class RestoreSeedDataService implements RestoreSeedDataUseCase {

    private final SeedDataRestorer seedDataRestorer;

    public RestoreSeedDataService(SeedDataRestorer seedDataRestorer) {
        this.seedDataRestorer = seedDataRestorer;
    }

    @Override
    @Transactional
    public void restore() {
        seedDataRestorer.restore();
    }
}
