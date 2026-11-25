package com.cocos.challenge.infrastructure.adapter.in.rest.error;

import com.cocos.challenge.domain.exception.DomainException;
import com.cocos.challenge.domain.exception.IdempotencyConflictException;
import com.cocos.challenge.domain.exception.InvalidRequestException;
import com.cocos.challenge.domain.exception.NotFoundException;
import com.cocos.challenge.domain.exception.OrderNotCancellableException;
import com.cocos.challenge.domain.exception.OrderNotExecutableException;
import com.cocos.challenge.domain.exception.SearchTermTooShortException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.InputCoercionException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * RFC 9457 error mapping of spec 003 / TEC-19. Internal exception details never reach an API response.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Map<Class<? extends DomainException>, ApiErrorCode> DOMAIN_CODES = Map.of(
            InvalidRequestException.class, ApiErrorCode.VALIDATION_ERROR,
            NotFoundException.class, ApiErrorCode.NOT_FOUND,
            IdempotencyConflictException.class, ApiErrorCode.IDEMPOTENCY_CONFLICT,
            OrderNotCancellableException.class, ApiErrorCode.ORDER_NOT_CANCELLABLE,
            OrderNotExecutableException.class, ApiErrorCode.ORDER_NOT_EXECUTABLE,
            SearchTermTooShortException.class, ApiErrorCode.SEARCH_TERM_TOO_SHORT);

    @ExceptionHandler({ApiException.class, InvalidRequestException.class, NotFoundException.class,
            IdempotencyConflictException.class, OrderNotCancellableException.class,
            OrderNotExecutableException.class, SearchTermTooShortException.class})
    ResponseEntity<Object> handleExpectedException(Exception exception, WebRequest request) {
        ApiErrorCode code = exception instanceof ApiException apiException
                ? apiException.code() : domainCode((DomainException) exception);
        return problem(code, code.status(), exception.getMessage(), new HttpHeaders(), request);
    }

    private static ApiErrorCode domainCode(DomainException exception) {
        return DOMAIN_CODES.entrySet().stream()
                .filter(entry -> entry.getKey().isInstance(exception))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow();
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        if (exception instanceof MethodArgumentNotValidException validationException) {
            List<FieldError> errors = validationException.getBindingResult().getFieldErrors().stream()
                    .map(fieldError -> new FieldError(fieldError.getField(), fieldError.getDefaultMessage()))
                    .toList();
            return validationProblem(errors, request);
        }
        if (exception instanceof HandlerMethodValidationException validationException) {
            return validationProblem(handlerMethodErrors(validationException), request);
        }
        if (exception instanceof HttpMessageNotReadableException notReadableException) {
            return messageNotReadableProblem(notReadableException, request);
        }
        if (exception instanceof MethodArgumentTypeMismatchException mismatchException) {
            return problem(ApiErrorCode.VALIDATION_ERROR, ApiErrorCode.VALIDATION_ERROR.status(),
                    "Request validation failed: '" + mismatchException.getName()
                            + "' has an invalid type or format.", new HttpHeaders(), request);
        }
        if (exception instanceof MissingServletRequestParameterException missingParameterException) {
            return problem(ApiErrorCode.VALIDATION_ERROR, ApiErrorCode.VALIDATION_ERROR.status(),
                    "Request validation failed: '" + missingParameterException.getParameterName()
                            + "' is required.", new HttpHeaders(), request);
        }
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

    private static List<FieldError> handlerMethodErrors(HandlerMethodValidationException exception) {
        List<FieldError> errors = new ArrayList<>();
        for (ParameterValidationResult result : exception.getParameterValidationResults()) {
            String parameter = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable resolvable : result.getResolvableErrors()) {
                String field = resolvable instanceof org.springframework.validation.FieldError fieldError
                        ? fieldError.getField()
                        : parameter;
                errors.add(new FieldError(field, resolvable.getDefaultMessage()));
            }
        }
        return errors;
    }

    private ResponseEntity<Object> messageNotReadableProblem(HttpMessageNotReadableException exception,
            WebRequest request) {
        Throwable cause = exception.getCause();
        while (cause != null) {
            if (cause instanceof UnrecognizedPropertyException unrecognized) {
                return validationProblem(List.of(new FieldError(unrecognized.getPropertyName(),
                        "is not a recognized field")), request);
            }
            if (cause instanceof MismatchedInputException || cause instanceof InputCoercionException) {
                String path = fieldPath((JacksonException) cause);
                if (!path.isEmpty()) {
                    return validationProblem(List.of(new FieldError(path, "has an invalid type or format")), request);
                }
                break;
            }
            cause = cause.getCause();
        }
        return problem(ApiErrorCode.VALIDATION_ERROR, ApiErrorCode.VALIDATION_ERROR.status(),
                "Malformed JSON request body.", new HttpHeaders(), request);
    }

    private static String fieldPath(JacksonException exception) {
        return exception.getPath().stream()
                .map(JacksonException.Reference::getPropertyName)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("."));
    }

    private ResponseEntity<Object> validationProblem(List<FieldError> errors, WebRequest request) {
        List<FieldError> sorted = errors.stream()
                .sorted(Comparator.comparing(FieldError::field).thenComparing(FieldError::message))
                .toList();
        String detail = sorted.isEmpty()
                ? "Request validation failed."
                : "Request validation failed: " + sorted.stream()
                        .map(error -> "'" + error.field() + "' " + error.message())
                        .collect(Collectors.joining("; ")) + ".";
        return problem(ApiErrorCode.VALIDATION_ERROR, ApiErrorCode.VALIDATION_ERROR.status(), detail, sorted,
                new HttpHeaders(), request);
    }

    private ResponseEntity<Object> problem(ApiErrorCode code, HttpStatusCode status, String detail,
            HttpHeaders headers, WebRequest request) {
        return problem(code, status, detail, null, headers, request);
    }

    private ResponseEntity<Object> problem(ApiErrorCode code, HttpStatusCode status, String detail,
            List<FieldError> errors, HttpHeaders headers, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(code.type());
        problem.setTitle(code.title());
        problem.setDetail(detail);
        problem.setInstance(URI.create(((ServletWebRequest) request).getRequest().getRequestURI()));
        problem.setProperty("code", code.name());
        if (errors != null) {
            problem.setProperty("errors", errors);
        }
        return ResponseEntity.status(status).headers(headers).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    /**
     * One field-level validation failure as it appears in the {@code errors} property of the Problem.
     */
    public record FieldError(String field, String message) {
    }
}
