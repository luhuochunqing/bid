package com.xiyu.bid.bidmatch.controller;

import com.xiyu.bid.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
        BidMatchEvaluationController.class,
        BidMatchModelController.class,
        BidMatchTenderScoreController.class
})
public class BidMatchApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(409, exception.getMessage()));
    }
}
