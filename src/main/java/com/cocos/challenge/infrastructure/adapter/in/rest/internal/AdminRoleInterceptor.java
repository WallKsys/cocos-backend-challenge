package com.cocos.challenge.infrastructure.adapter.in.rest.internal;

import com.cocos.challenge.infrastructure.adapter.in.rest.error.ApiErrorCode;
import com.cocos.challenge.infrastructure.adapter.in.rest.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/** Hides internal routes from every role except the exact {@code ADMIN} role (TEC-18). */
public class AdminRoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"ADMIN".equals(request.getHeader("X-Role"))) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "Resource not found.");
        }
        return true;
    }
}
