package com.xiyu.bid.resources.controller;

import com.xiyu.bid.resources.service.CaBusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(basePackages = "com.xiyu.bid.resources")
public class CaExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CaExceptionHandler.class);

    @ExceptionHandler(CaBusinessException.class)
    public ResponseEntity<Map<String, Object>> handleCaBusiness(CaBusinessException ex) {
        HttpStatus status = switch (ex.getErrorCode()) {
            case "AUTH_REQUIRED" -> HttpStatus.UNAUTHORIZED;
            case "PERMISSION_DENIED" -> HttpStatus.FORBIDDEN;
            case "USER_NOT_FOUND" -> HttpStatus.UNAUTHORIZED;
            default -> HttpStatus.BAD_REQUEST;
        };
        log.warn("CA business exception: [{}] {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(status).body(Map.of(
                "success", false,
                "code", status.value(),
                "msg", ex.getMessage()
        ));
    }
}
