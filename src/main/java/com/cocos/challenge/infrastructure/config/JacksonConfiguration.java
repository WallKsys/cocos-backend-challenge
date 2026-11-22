package com.cocos.challenge.infrastructure.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfiguration {

    @Bean
    JsonMapperBuilderCustomizer rejectScalarCoercions() {
        return builder -> {
        };
    }
}
