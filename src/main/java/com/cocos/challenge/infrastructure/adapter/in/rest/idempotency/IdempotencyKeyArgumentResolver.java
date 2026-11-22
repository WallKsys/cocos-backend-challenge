package com.cocos.challenge.infrastructure.adapter.in.rest.idempotency;

import com.cocos.challenge.infrastructure.adapter.in.rest.error.ApiErrorCode;
import com.cocos.challenge.infrastructure.adapter.in.rest.error.ApiException;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class IdempotencyKeyArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(IdempotencyKeyHeader.class)
                && parameter.getParameterType() == String.class;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String key = webRequest.getHeader("Idempotency-Key");
        if (key == null || key.isBlank()) {
            throw new ApiException(ApiErrorCode.MISSING_IDEMPOTENCY_KEY,
                    "The 'Idempotency-Key' header is required for this operation.");
        }
        if (!key.matches("^[\\x21-\\x7E]{1,255}$")) {
            throw new ApiException(ApiErrorCode.VALIDATION_ERROR,
                    "The 'Idempotency-Key' header must be 1 to 255 visible ASCII characters.");
        }
        return key;
    }
}
