package com.fgw.config;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(403).body(Map.of(
                "error",   "Forbidden",
                "message", "You do not have permission to access this resource",
                "status",  403
        ));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException ex) {
        log.error("Runtime error: {}", ex.getMessage());
        return ResponseEntity.status(500).body(Map.of(
                "error",   "Internal Server Error",
                "message", ex.getMessage(),
                "status",  500
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        log.error("Unhandled error: {}", ex.getMessage());
        return ResponseEntity.status(500).body(Map.of(
                "error",   "Internal Server Error",
                "message", ex.getMessage(),
                "status",  500
        ));
    }
}
