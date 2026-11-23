package com.cocos.challenge.infrastructure.config;

import com.cocos.challenge.application.port.out.BusinessMetrics;
import com.cocos.challenge.infrastructure.adapter.out.metrics.MicrometerBusinessMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfiguration {

    @Bean
    BusinessMetrics businessMetrics(MeterRegistry registry) {
        return new MicrometerBusinessMetrics(registry);
    }
}
