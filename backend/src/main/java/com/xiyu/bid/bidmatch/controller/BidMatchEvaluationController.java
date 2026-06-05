package com.xiyu.bid.bidmatch.controller;

import com.xiyu.bid.bidmatch.application.BidMatchEvaluationAppService;
import com.xiyu.bid.bidmatch.dto.BidMatchEvaluationResponse;
import com.xiyu.bid.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bid-match/evaluations")
@RequiredArgsConstructor
public class BidMatchEvaluationController {

    private final BidMatchEvaluationAppService evaluationAppService;

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<BidMatchEvaluationResponse>> getEvaluation(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(evaluationAppService.get(id)));
    }
}
