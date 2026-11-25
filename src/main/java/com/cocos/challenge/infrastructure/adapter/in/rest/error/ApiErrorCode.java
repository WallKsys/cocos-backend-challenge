package com.cocos.challenge.infrastructure.adapter.in.rest.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.net.URI;

public enum ApiErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation error", "validation-error"),
    MISSING_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "Missing idempotency key", "missing-idempotency-key"),
    SEARCH_TERM_TOO_SHORT(HttpStatus.BAD_REQUEST, "Search term too short", "search-term-too-short"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found", "not-found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed", "method-not-allowed"),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "Not acceptable", "not-acceptable"),
    ORDER_NOT_CANCELLABLE(HttpStatus.CONFLICT, "Order not cancellable", "order-not-cancellable"),
    ORDER_NOT_EXECUTABLE(HttpStatus.CONFLICT, "Order not executable", "order-not-executable"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type", "unsupported-media-type"),
    IDEMPOTENCY_CONFLICT(HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency key conflict", "idempotency-conflict"),
    REQUEST_ERROR(HttpStatus.BAD_REQUEST, "Request error", "request-error"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", "internal-error");

    private static final String BASE_TYPE = "https://api.cocos.example/errors/";

    private final HttpStatusCode status;
    private final String title;
    private final String slug;

    ApiErrorCode(HttpStatusCode status, String title, String slug) {
        this.status = status;
        this.title = title;
        this.slug = slug;
    }

    public HttpStatusCode status() {
        return status;
    }

    public String title() {
        return title;
    }

    public URI type() {
        return URI.create(BASE_TYPE + slug);
    }
}
