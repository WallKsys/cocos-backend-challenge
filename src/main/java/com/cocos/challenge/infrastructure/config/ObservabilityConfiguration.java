package com.cocos.challenge.infrastructure.config;

import com.cocos.challenge.application.port.out.BusinessMetrics;
import com.cocos.challenge.infrastructure.adapter.out.metrics.MicrometerBusinessMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metrics and OpenTelemetry logback appender wiring. */
@Configuration
public class ObservabilityConfiguration {

    @Bean
    BusinessMetrics businessMetrics(MeterRegistry registry) {
        return new MicrometerBusinessMetrics(registry);
    }

    @Bean
    InitializingBean openTelemetryLogbackAppenderInstaller(OpenTelemetry openTelemetry) {
        return () -> OpenTelemetryAppender.install(openTelemetry);
    }
}
