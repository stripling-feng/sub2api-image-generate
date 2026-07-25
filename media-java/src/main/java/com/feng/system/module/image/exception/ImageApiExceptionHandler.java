package com.feng.system.module.image.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = {"com.feng.system.module.image", "com.feng.system.module.video"})
public class ImageApiExceptionHandler {

    @ExceptionHandler(ImageApiException.class)
    public ResponseEntity<Map<String, Object>> handleImageApi(ImageApiException exception) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", exception.getMessage());
        if (exception.getCode() != null) body.put("code", exception.getCode());
        if (exception.getPayload() != null) body.put("payload", exception.getPayload());
        return ResponseEntity.status(exception.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
        return ResponseEntity.unprocessableEntity().body(Map.of(
                "error", "Invalid request.",
                "details", exception.getBindingResult().getFieldErrors().stream()
                        .map(error -> Map.of("path", new String[]{error.getField()}, "message", error.getDefaultMessage()))
                        .toList()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception) {
        return ResponseEntity.internalServerError().body(Map.of("error",
                exception.getMessage() == null ? "Internal server error." : exception.getMessage()));
    }
}
