package com.riyura.backend.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        boolean success,
        int status,
        String error,
        String message,
        Instant timestamp) {

    /**
     * Factory method — creates an error response from status + message.
     */
    public static ApiErrorResponse of(HttpStatus httpStatus, String message) {
        return new ApiErrorResponse(
                false,
                httpStatus.value(),
                httpStatus.getReasonPhrase(),
                message,
                Instant.now());
    }

    /**
     * Factory method — creates an error response with a custom error label.
     */
    public static ApiErrorResponse of(HttpStatus httpStatus, String error, String message) {
        return new ApiErrorResponse(
                false,
                httpStatus.value(),
                error,
                message,
                Instant.now());
    }

    /**
     * Convenience: wraps this error in a ResponseEntity.
     */
    public ResponseEntity<ApiErrorResponse> toResponseEntity() {
        return ResponseEntity.status(this.status).body(this);
    }

    public static ResponseEntity<ApiErrorResponse> respond(HttpStatus httpStatus, String message) {
        return of(httpStatus, message).toResponseEntity();
    }

    public static ResponseEntity<ApiErrorResponse> respond(HttpStatus httpStatus, String error, String message) {
        return of(httpStatus, error, message).toResponseEntity();
    }
}
