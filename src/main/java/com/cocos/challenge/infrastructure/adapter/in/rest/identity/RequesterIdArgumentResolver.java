package com.cocos.challenge.infrastructure.adapter.in.rest.identity;

import com.cocos.challenge.domain.model.UserId;
import com.cocos.challenge.infrastructure.adapter.in.rest.error.ApiErrorCode;
import com.cocos.challenge.infrastructure.adapter.in.rest.error.ApiException;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Populates {@link RequesterId} parameters of type {@link UserId} only from the {@code X-User-Id} header
 * (TEC-18): trimmed, a positive integer up to 2147483647.
 */
public class RequesterIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(RequesterId.class) && parameter.getParameterType() == UserId.class;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String header = webRequest.getHeader("X-User-Id");
        String value = header == null ? "" : header.trim();
        if (value.isBlank()) {
            throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "The 'X-User-Id' header is required.");
        }
        if (!value.matches("\\d+") || value.length() > 10) {
            throw new ApiException(ApiErrorCode.VALIDATION_ERROR,
                    "The 'X-User-Id' header must be a positive integer.");
        }
        long id = Long.parseLong(value);
        if (id < 1 || id > Integer.MAX_VALUE) {
            throw new ApiException(ApiErrorCode.VALIDATION_ERROR,
                    "The 'X-User-Id' header must be a positive integer.");
        }
        return new UserId(id);
    }
}
