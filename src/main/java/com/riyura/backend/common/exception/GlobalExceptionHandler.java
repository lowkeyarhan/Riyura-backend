package com.riyura.backend.common.exception;

import com.riyura.backend.common.dto.ApiErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

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

        @ExceptionHandler(ResponseStatusException.class)
        public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException ex) {
                HttpStatusCode statusCode = ex.getStatusCode();
                HttpStatus httpStatus = HttpStatus.resolve(statusCode.value());
                if (httpStatus == null) {
                        httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
                }
                return ApiErrorResponse.respond(httpStatus, ex.getReason());
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
                log.error("Unhandled exception", ex);
                return ApiErrorResponse.respond(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        }
}
