package com.cocos.challenge.infrastructure.config;

import com.cocos.challenge.infrastructure.adapter.in.rest.identity.RequesterIdArgumentResolver;
import com.cocos.challenge.infrastructure.adapter.in.rest.internal.AdminRoleInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/** MVC argument resolvers and the administrator guard of spec 003 (TEC-18). */
@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new RequesterIdArgumentResolver());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminRoleInterceptor()).addPathPatterns("/api/internal/**");
    }
}
