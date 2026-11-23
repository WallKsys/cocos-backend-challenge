package com.cocos.challenge.infrastructure.config;

import com.cocos.challenge.application.port.in.SearchInstrumentsQuery;
import com.cocos.challenge.application.port.out.InstrumentSearchRepository;
import com.cocos.challenge.application.service.SearchInstrumentsService;
import com.cocos.challenge.infrastructure.adapter.out.persistence.JdbcInstrumentSearchRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration
public class InstrumentSearchConfiguration {

    @Bean
    InstrumentSearchRepository instrumentSearchRepository(JdbcClient jdbcClient) {
        return new JdbcInstrumentSearchRepository(jdbcClient);
    }

    @Bean
    SearchInstrumentsQuery searchInstrumentsQuery(InstrumentSearchRepository repository) {
        return new SearchInstrumentsService(repository);
    }
}
