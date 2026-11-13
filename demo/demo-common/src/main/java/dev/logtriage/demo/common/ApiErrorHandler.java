package dev.logtriage.demo.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Maps failures to HTTP statuses and logs them the way a typical service
 * does: 4xx as a WARN without a stack trace, 5xx as an ERROR with one.
 */
@RestControllerAdvice
public class ApiErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    @ExceptionHandler(ResourceAccessException.class)
    ResponseEntity<ProblemDetail> downstreamUnreachable(ResourceAccessException e, HttpServletRequest request) {
        HttpStatus status = isTimeout(e) ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE;
        return respond(status, e, request);
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.net.http.HttpTimeoutException || t instanceof java.net.SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    @ExceptionHandler(RestClientResponseException.class)
    ResponseEntity<ProblemDetail> downstreamError(RestClientResponseException e, HttpServletRequest request) {
        HttpStatus status = e.getStatusCode().is4xxClientError()
                ? HttpStatus.valueOf(e.getStatusCode().value())
                : HttpStatus.BAD_GATEWAY;
        return respond(status, e, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unhandled(Exception e, HttpServletRequest request) {
        ResponseStatus annotated = AnnotatedElementUtils.findMergedAnnotation(e.getClass(), ResponseStatus.class);
        HttpStatus status = annotated != null ? annotated.code() : HttpStatus.INTERNAL_SERVER_ERROR;
        return respond(status, e, request);
    }

    private ResponseEntity<ProblemDetail> respond(HttpStatus status, Exception e, HttpServletRequest request) {
        if (status.is5xxServerError()) {
            log.error("Request {} {} failed with {}", request.getMethod(), request.getRequestURI(), status.value(), e);
        } else {
            log.warn("Request {} {} rejected with {}: {}", request.getMethod(), request.getRequestURI(),
                    status.value(), e.getMessage());
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        return ResponseEntity.status(status).body(problem);
    }
}
