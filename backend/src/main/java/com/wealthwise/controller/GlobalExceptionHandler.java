package com.wealthwise.controller;

import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

/**
 * Centralized exception handler for all controllers.
 *
 * Ensures:
 *  - JWT/auth errors → 401 (not 500)
 *  - File-too-large → 413 with readable message
 *  - Unexpected server errors → 500 with sanitized message (no stack traces)
 *  - Business logic errors (IllegalArgument, IllegalState) → 400
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** JWT expired, malformed, or invalid signature */
    @ExceptionHandler({JwtException.class, SecurityException.class})
    public ResponseEntity<Map<String, String>> handleJwtException(Exception e) {
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("error", "Authentication failed — please log in again"));
    }

    /** Business rule violations */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> handleBadRequest(RuntimeException e) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", sanitize(e.getMessage())));
    }

    /** PDF too large */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleFileTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity
            .status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(Map.of("error", "File too large — maximum allowed size is 10 MB"));
    }

    /** Catch-all: prevents stack traces from leaking to clients */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneral(Exception e) {
        // Log internally (Spring Boot auto-logs to console); never expose internals to client
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", "An unexpected error occurred. Please try again later."));
    }

    /**
     * Sanitize RuntimeException messages before sending to client.
     * Strips Java class names and package paths from messages.
     */
    private String sanitize(String msg) {
        if (msg == null) return "An error occurred";
        // Remove anything that looks like a Java class path (com.x.y.ClassName)
        return msg.replaceAll("(?i)com\\.\\w+\\.\\w+(\\.\\w+)*:\\s*", "")
                  .replaceAll("(?i)java\\.\\w+\\.\\w+(\\.\\w+)*:\\s*", "")
                  .trim();
    }
}
