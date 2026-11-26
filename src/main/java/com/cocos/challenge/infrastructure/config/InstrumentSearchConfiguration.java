package com.cocos.challenge.infrastructure.config;

import com.cocos.challenge.application.port.in.SearchInstrumentsQuery;
import com.cocos.challenge.application.port.out.InstrumentRepository;
import com.cocos.challenge.application.service.SearchInstrumentsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InstrumentSearchConfiguration {

    @Bean
    SearchInstrumentsQuery searchInstrumentsQuery(InstrumentRepository repository) {
        return new SearchInstrumentsService(repository);
    }
}
