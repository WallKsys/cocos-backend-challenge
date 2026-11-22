package com.cocos.challenge.infrastructure.adapter.in.rest.internal;

import com.cocos.challenge.infrastructure.adapter.in.rest.error.ApiErrorCode;
import com.cocos.challenge.infrastructure.adapter.in.rest.error.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

/** Hides internal routes from every role except the exact {@code ADMIN} role (TEC-18). */
public class AdminRoleFilter extends OncePerRequestFilter {

    private final HandlerExceptionResolver handlerExceptionResolver;

    public AdminRoleFilter(HandlerExceptionResolver handlerExceptionResolver) {
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!"ADMIN".equals(request.getHeader("X-Role"))) {
            handlerExceptionResolver.resolveException(request, response, null,
                    new ApiException(ApiErrorCode.NOT_FOUND, "Resource not found."));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
