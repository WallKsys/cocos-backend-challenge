package com.cocos.challenge.infrastructure.config;

import com.cocos.challenge.infrastructure.adapter.in.rest.idempotency.IdempotencyKeyArgumentResolver;
import com.cocos.challenge.infrastructure.adapter.in.rest.identity.RequesterIdArgumentResolver;
import com.cocos.challenge.infrastructure.adapter.in.rest.internal.AdminRoleFilter;
import com.cocos.challenge.infrastructure.adapter.in.rest.tracing.RequestIdFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

import java.util.List;

/** MVC argument resolvers, the administrator guard and the correlation-id filter. */
@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new RequesterIdArgumentResolver());
        resolvers.add(new IdempotencyKeyArgumentResolver());
    }

    @Bean
    RequestIdFilter requestIdFilter() {
        return new RequestIdFilter();
    }

    @Bean
    FilterRegistrationBean<AdminRoleFilter> adminRoleFilter(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        FilterRegistrationBean<AdminRoleFilter> registration = new FilterRegistrationBean<>(
                new AdminRoleFilter(handlerExceptionResolver));
        registration.addUrlPatterns("/api/internal/*");
        return registration;
    }

    @Bean
    JsonMapperBuilderCustomizer rejectScalarCoercions() {
        return builder -> builder
                .withCoercionConfig(LogicalType.Textual, config -> config
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                .withCoercionConfig(LogicalType.Integer, config -> config
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail));
    }
}
