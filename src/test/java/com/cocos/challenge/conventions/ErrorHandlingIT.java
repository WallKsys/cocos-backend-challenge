package com.cocos.challenge.conventions;

import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 003 US1: domain and Spring MVC exceptions have the exact RFC 9457 shape and error-catalog values.
 */
@IntegrationTest
@Import(ProbeControllers.class)
class ErrorHandlingIT {

    @Autowired
    RestTestClient restTestClient;

    @Test
    void notFoundDomainExceptionUsesItsDetail() {
        assertProblem(get("/test/conventions/throw/not-found"), 404, "NOT_FOUND", "Resource not found", "not-found",
                "Order 1234 was not found.", "/test/conventions/throw/not-found");
    }

    @Test
    void domainExceptionSubclassUsesTheDomainMapping() {
        assertProblem(get("/test/conventions/throw/not-found-subclass"), 404, "NOT_FOUND", "Resource not found",
                "not-found", "Order subclass was not found.", "/test/conventions/throw/not-found-subclass");
    }

    @Test
    void unknownApiRouteIsNotFound() {
        assertProblem(get("/api/does-not-exist"), 404, "NOT_FOUND", "Resource not found", "not-found",
                "Resource not found.", "/api/does-not-exist");
    }

    @Test
    void idempotencyConflictUsesTheExactMessage() {
        assertProblem(get("/test/conventions/throw/idempotency-conflict"), 422, "IDEMPOTENCY_CONFLICT",
                "Idempotency key conflict", "idempotency-conflict",
                "Idempotency key 'k1' was already used for a different request.",
                "/test/conventions/throw/idempotency-conflict");
    }

    @Test
    void orderNotCancellableUsesTheExactMessage() {
        assertProblem(get("/test/conventions/throw/order-not-cancellable"), 409, "ORDER_NOT_CANCELLABLE",
                "Order not cancellable", "order-not-cancellable",
                "Order 1234 cannot be cancelled because its status is FILLED. Only NEW orders can be cancelled.",
                "/test/conventions/throw/order-not-cancellable");
    }

    @Test
    void invalidRequestUsesItsDetail() {
        assertProblem(get("/test/conventions/throw/invalid-request"), 400, "VALIDATION_ERROR", "Validation error",
                "validation-error", "The request is invalid.", "/test/conventions/throw/invalid-request");
    }

    @Test
    void unsupportedHttpMethodHasAStableProblem() {
        assertProblem(restTestClient.post().uri("/test/conventions/throw/not-found").exchange(), 405,
                "METHOD_NOT_ALLOWED", "Method not allowed", "method-not-allowed",
                "Method POST is not supported for this resource.", "/test/conventions/throw/not-found");
    }

    @Test
    void unsupportedContentTypeHasAStableProblem() {
        assertProblem(restTestClient.post().uri("/test/conventions/validate")
                        .contentType(MediaType.parseMediaType("application/octet-stream;charset=UTF-8"))
                        .body("not JSON").exchange(), 415,
                "UNSUPPORTED_MEDIA_TYPE", "Unsupported media type", "unsupported-media-type",
                "Content type 'application/octet-stream;charset=UTF-8' is not supported.", "/test/conventions/validate");
    }

    @Test
    void unacceptableAcceptHeaderHasAStableProblem() {
        assertProblem(restTestClient.post().uri("/test/conventions/validate")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_XML)
                        .body("{\"amount\":\"1.00\",\"quantity\":1}").exchange(), 406,
                "NOT_ACCEPTABLE", "Not acceptable", "not-acceptable",
                "The requested media type is not supported.", "/test/conventions/validate");
    }

    @Test
    void unexpectedExceptionNeverLeaksItsMessage() {
        assertProblem(get("/test/conventions/throw/unexpected"), 500, "INTERNAL_ERROR", "Internal error",
                "internal-error", "An unexpected error occurred.", "/test/conventions/throw/unexpected");

        String body = get("/test/conventions/throw/unexpected").returnResult(String.class).getResponseBody();
        assertThat(body).doesNotContain("secret internal detail");
    }

    @Test
    void invalidBodyListsEachFieldErrorInEnglish() {
        assertProblemWithErrors(post("{\"amount\":null,\"quantity\":0}"), 400, "VALIDATION_ERROR",
                "Validation error", "validation-error",
                "Request validation failed: 'amount' must not be null; 'quantity' must be greater than 0.",
                "/test/conventions/validate",
                List.of(new FieldError("amount", "must not be null"),
                        new FieldError("quantity", "must be greater than 0")));
    }

    @Test
    void malformedJsonBodyHasAStableProblem() {
        assertProblem(post("{\"amount\": \"1.00\","), 400, "VALIDATION_ERROR", "Validation error",
                "validation-error", "Malformed JSON request body.", "/test/conventions/validate");
    }

    @Test
    void validationMessagesStayEnglishWithSpanishAcceptLanguage() {
        assertProblemWithErrors(restTestClient.post().uri("/test/conventions/validate")
                        .contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.ACCEPT_LANGUAGE, "es")
                        .body("{\"amount\":null,\"quantity\":0}").exchange(), 400, "VALIDATION_ERROR",
                "Validation error", "validation-error",
                "Request validation failed: 'amount' must not be null; 'quantity' must be greater than 0.",
                "/test/conventions/validate",
                List.of(new FieldError("amount", "must not be null"),
                        new FieldError("quantity", "must be greater than 0")));
    }

    @Test
    void unknownBodyFieldIsRejected() {
        assertProblemWithErrors(post("{\"amount\":\"1.00\",\"quantity\":1,\"userId\":1}"), 400,
                "VALIDATION_ERROR", "Validation error", "validation-error",
                "Request validation failed: 'userId' is not a recognized field.",
                "/test/conventions/validate",
                List.of(new FieldError("userId", "is not a recognized field")));
    }

    private RestTestClient.ResponseSpec get(String path) {
        return restTestClient.get().uri(path).exchange();
    }

    private RestTestClient.ResponseSpec post(String body) {
        return restTestClient.post().uri("/test/conventions/validate")
                .contentType(MediaType.APPLICATION_JSON).body(body).exchange();
    }

    private Problem assertProblemWithErrors(RestTestClient.ResponseSpec response, int status, String code,
            String title, String slug, String detail, String instance, List<FieldError> errors) {
        Problem problem = assertProblemFields(response, status, code, title, slug, detail, instance);
        assertThat(problem.errors()).containsExactlyElementsOf(errors);
        return problem;
    }

    private Problem assertProblem(RestTestClient.ResponseSpec response, int status, String code, String title,
            String slug, String detail, String instance) {
        Problem problem = assertProblemFields(response, status, code, title, slug, detail, instance);
        assertThat(problem.errors()).isNull();
        return problem;
    }

    private Problem assertProblemFields(RestTestClient.ResponseSpec response, int status, String code, String title,
            String slug, String detail, String instance) {
        EntityExchangeResult<Problem> result = response.returnResult(Problem.class);
        assertThat(result.getStatus().value()).isEqualTo(status);
        assertThat(result.getResponseHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        Problem problem = result.getResponseBody();
        assertThat(problem).isNotNull();
        assertThat(problem.type()).isEqualTo("https://api.cocos.example/errors/" + slug);
        assertThat(problem.title()).isEqualTo(title);
        assertThat(problem.status()).isEqualTo(status);
        assertThat(problem.code()).isEqualTo(code);
        assertThat(problem.detail()).isEqualTo(detail);
        assertThat(problem.instance()).isEqualTo(instance);
        return problem;
    }

    private record Problem(String type, String title, int status, String code, String detail, String instance,
            List<FieldError> errors) {
    }

    private record FieldError(String field, String message) {
    }
}
