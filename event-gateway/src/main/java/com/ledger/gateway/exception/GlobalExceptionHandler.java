package com.ledger.gateway.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.UnexpectedRollbackException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;

import java.time.Instant;
import java.time.format.DateTimeParseException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final Counter errorCounter;

    public GlobalExceptionHandler(MeterRegistry meterRegistry) {
        this.errorCounter = Counter.builder("events_errors_total").description("Total errors handling /events requests").register(meterRegistry);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleJsonParseException(HttpMessageNotReadableException ex) {
        logger.warn("Bad Request: Invalid JSON format. {}", ex.getMessage());
        errorCounter.increment();

        String message = "Invalid JSON format.";
        // Check for a more specific cause, like a timestamp parsing error
        if (ex.getCause() instanceof InvalidFormatException && ex.getCause().getCause() instanceof DateTimeParseException) {
            message = "Invalid timestamp format. Please use ISO 8601 format (e.g., yyyy-MM-dd'T'HH:mm:ss'Z').";
        }
        ErrorResponse error = new ErrorResponse(
                "BAD_REQUEST",
                message,
                Instant.now()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        logger.warn("Bad Request: {}", ex.getMessage());
        errorCounter.increment();
        ErrorResponse error = new ErrorResponse(
                "BAD_REQUEST",
                ex.getMessage(),
                Instant.now()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({DataIntegrityViolationException.class, UnexpectedRollbackException.class})
    public ResponseEntity<ErrorResponse> handleDuplicateKeyException(Exception ex) {
        // Duplicate event (unique constraint) should be treated as idempotent - return IGNORED
        logger.warn("Duplicate event detected or transaction rolled back: {}", ex.getMessage());
        errorCounter.increment();
        ErrorResponse ignored = new ErrorResponse(
                "IGNORED",
                "Event already processed",
                Instant.now()
        );
        return new ResponseEntity<>(ignored, HttpStatus.OK);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex) {
        // Handle specifically, circuit breaker fallback exception
        if (ex.getMessage() != null && ex.getMessage().contains("currently unavailable")) {
            logger.warn("Service Unavailable: {}", ex.getMessage());
            errorCounter.increment();
            ErrorResponse error = new ErrorResponse(
                    "SERVICE_UNAVAILABLE",
                    "The Account Service is currently down. Please try again later.",
                    Instant.now()
            );
            return new ResponseEntity<>(error, HttpStatus.SERVICE_UNAVAILABLE);
        }

        // Generic fallback for other RuntimeExceptions
        logger.error("Internal Server Error: ", ex);
        errorCounter.increment();
        ErrorResponse error = new ErrorResponse(
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred.",
                Instant.now()
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        logger.error("Internal Server Error: ", ex);
        errorCounter.increment();
        ErrorResponse error = new ErrorResponse(
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred.",
                Instant.now()
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}