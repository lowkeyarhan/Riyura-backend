package com.riyura.backend.common.exception;

import com.riyura.backend.common.dto.ApiErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // @formatter:off

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ApiErrorResponse.respond(HttpStatus.BAD_REQUEST, "Validation failed", errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        String errors = ex.getConstraintViolations().stream()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .collect(Collectors.joining(", "));
        return ApiErrorResponse.respond(HttpStatus.BAD_REQUEST, "Validation failed", errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ApiErrorResponse.respond(HttpStatus.BAD_REQUEST, "Bad Request",
                "Invalid value for parameter: " + ex.getName());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ApiErrorResponse.respond(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException ex) {
        return ApiErrorResponse.respond(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NoSuchElementException ex) {
        return ApiErrorResponse.respond(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ApiErrorResponse.respond(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        HttpStatusCode statusCode = ex.getStatusCode();
        HttpStatus httpStatus = HttpStatus.resolve(statusCode.value());
        if (httpStatus == null) httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        return ApiErrorResponse.respond(httpStatus, ex.getReason());
    }

    // Catches the case where a request with Accept:text/event-stream hits a non-SSE endpoint
    // or when an SSE endpoint throws before the emitter is created.
    // By explicitly returning JSON here we bypass the broken content-negotiation fallback.
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_ACCEPTABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrorResponse.of(HttpStatus.NOT_ACCEPTABLE, "Not Acceptable",
                        "The requested media type is not supported by this endpoint."));
    }

    @ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsableException(org.springframework.web.context.request.async.AsyncRequestNotUsableException ex) {
        // Client disconnected during an SSE or async request stream.
        // No need to return an ApiErrorResponse because the connection is already gone,
        // and doing so causes HttpMessageNotWritableException because content type is text/event-stream.
        log.warn("Client disconnected during async request: {}", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnexpected(Exception ex) {
        // If the response is already committed (e.g. SSE stream), we can't write a JSON response anymore.
        if (ex instanceof org.springframework.http.converter.HttpMessageNotWritableException) {
            log.warn("Failed to write error response (connection likely closed): {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        
        log.error("Unhandled exception", ex);
        return ApiErrorResponse.respond(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    // @formatter:on
}
