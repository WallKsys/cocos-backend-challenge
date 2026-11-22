package com.cocos.challenge.infrastructure.adapter.in.rest.error;

import com.cocos.challenge.domain.exception.DomainException;
import com.cocos.challenge.domain.exception.IdempotencyConflictException;
import com.cocos.challenge.domain.exception.InvalidRequestException;
import com.cocos.challenge.domain.exception.NotFoundException;
import com.cocos.challenge.domain.exception.OrderNotCancellableException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.Map;

/**
 * RFC 9457 error mapping of spec 003 / TEC-19. Internal exception details never reach an API response.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Map<Class<? extends DomainException>, ApiErrorCode> DOMAIN_CODES = Map.of(
            InvalidRequestException.class, ApiErrorCode.VALIDATION_ERROR,
            NotFoundException.class, ApiErrorCode.NOT_FOUND,
            IdempotencyConflictException.class, ApiErrorCode.IDEMPOTENCY_CONFLICT,
            OrderNotCancellableException.class, ApiErrorCode.ORDER_NOT_CANCELLABLE);

    @ExceptionHandler({ApiException.class, InvalidRequestException.class, NotFoundException.class,
            IdempotencyConflictException.class, OrderNotCancellableException.class})
    ResponseEntity<Object> handleExpectedException(Exception exception, WebRequest request) {
        ApiErrorCode code = exception instanceof ApiException apiException
                ? apiException.code() : DOMAIN_CODES.get(exception.getClass());
        return problem(code, code.status(), exception.getMessage(), new HttpHeaders(), request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ApiErrorCode code;
        String detail;
        if (exception instanceof NoResourceFoundException) {
            code = ApiErrorCode.NOT_FOUND;
            detail = "Resource not found.";
        } else if (exception instanceof HttpRequestMethodNotSupportedException methodException) {
            code = ApiErrorCode.METHOD_NOT_ALLOWED;
            detail = "Method " + methodException.getMethod() + " is not supported for this resource.";
        } else if (exception instanceof HttpMediaTypeNotAcceptableException) {
            code = ApiErrorCode.NOT_ACCEPTABLE;
            detail = "The requested media type is not supported.";
        } else if (exception instanceof HttpMediaTypeNotSupportedException mediaTypeException) {
            code = ApiErrorCode.UNSUPPORTED_MEDIA_TYPE;
            detail = "Content type '" + mediaTypeException.getContentType() + "' is not supported.";
        } else if (status.is5xxServerError()) {
            code = ApiErrorCode.INTERNAL_ERROR;
            detail = "An unexpected error occurred.";
        } else {
            code = ApiErrorCode.REQUEST_ERROR;
            detail = body instanceof ProblemDetail problemDetail ? problemDetail.getDetail() : null;
        }
        return problem(code, status, detail, headers, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpectedException(Exception exception, WebRequest request) {
        logger.error("Unexpected exception while handling request", exception);
        return problem(ApiErrorCode.INTERNAL_ERROR, ApiErrorCode.INTERNAL_ERROR.status(),
                "An unexpected error occurred.", new HttpHeaders(), request);
    }

    private ResponseEntity<Object> problem(ApiErrorCode code, HttpStatusCode status, String detail,
            HttpHeaders headers, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(code.type());
        problem.setTitle(code.title());
        problem.setDetail(detail);
        problem.setInstance(URI.create(((ServletWebRequest) request).getRequest().getRequestURI()));
        problem.setProperty("code", code.name());
        return ResponseEntity.status(status).headers(headers).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
