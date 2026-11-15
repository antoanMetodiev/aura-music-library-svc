package com.aura.library.adapter.web;

import com.aura.library.adapter.web.dto.LibraryDtos.ApiError;
import com.aura.library.config.RequestIdFilter;
import com.aura.library.domain.service.LibraryExceptions;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every exception the library API can throw into the {@code {code, message, traceId}} shape
 * from Project-Info.md §49. Never forwards a stack trace to the client (§35, §49).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(LibraryExceptions.NotFound.class)
    public ResponseEntity<ApiError> notFound(LibraryExceptions.NotFound e) {
        return error(HttpStatus.NOT_FOUND, e.code(), e.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> noRoute(NoResourceFoundException e) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "No such endpoint");
    }

    @ExceptionHandler(LibraryExceptions.Forbidden.class)
    public ResponseEntity<ApiError> forbidden(LibraryExceptions.Forbidden e) {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", e.getMessage());
    }

    @ExceptionHandler(LibraryExceptions.LimitExceeded.class)
    public ResponseEntity<ApiError> limit(LibraryExceptions.LimitExceeded e) {
        return error(HttpStatus.UNPROCESSABLE_CONTENT, "LIMIT_EXCEEDED", e.getMessage());
    }

    @ExceptionHandler(LibraryExceptions.CatalogUnavailable.class)
    public ResponseEntity<ApiError> catalogUnavailable(LibraryExceptions.CatalogUnavailable e) {
        return error(HttpStatus.BAD_GATEWAY, "CATALOG_UNAVAILABLE", "The music catalog is currently unavailable");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class,
            ConstraintViolationException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiError> badRequest(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> internalError(Exception e) {
        log.error("Unhandled exception", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong on our side");
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        String traceId = MDC.get(RequestIdFilter.MDC_KEY);
        return ResponseEntity.status(status).body(new ApiError(code, message, traceId));
    }
}
