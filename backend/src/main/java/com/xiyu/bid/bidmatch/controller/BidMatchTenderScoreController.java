package com.xiyu.bid.bidmatch.controller;

import com.xiyu.bid.bidmatch.application.BidMatchEvaluationAppService;
import com.xiyu.bid.bidmatch.dto.BidMatchEvaluationResponse;
import com.xiyu.bid.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tenders/{tenderId}/match-score")
@RequiredArgsConstructor
public class BidMatchTenderScoreController {

    private final BidMatchEvaluationAppService evaluationAppService;

    @PostMapping("/evaluate")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<BidMatchEvaluationResponse>> evaluate(@PathVariable Long tenderId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("标讯匹配评分已生成", evaluationAppService.evaluate(tenderId)));
    }

    @GetMapping("/latest")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<BidMatchEvaluationResponse>> latest(@PathVariable Long tenderId) {
        return ResponseEntity.ok(ApiResponse.success(evaluationAppService.latest(tenderId)));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<BidMatchEvaluationResponse>>> history(@PathVariable Long tenderId) {
        return ResponseEntity.ok(ApiResponse.success(evaluationAppService.history(tenderId)));
    }
}
