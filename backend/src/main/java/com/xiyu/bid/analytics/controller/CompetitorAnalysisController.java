package com.xiyu.bid.analytics.controller;

import com.xiyu.bid.analytics.dto.CompetitorAnalysisRequest;
import com.xiyu.bid.analytics.dto.CompetitorAnalysisResponse;
import com.xiyu.bid.analytics.service.CompetitorAnalysisService;
import com.xiyu.bid.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class CompetitorAnalysisController {

    private final CompetitorAnalysisService competitorAnalysisService;

    @PostMapping("/competitor-analysis")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CompetitorAnalysisResponse>> analyzeCompetitors(
            @RequestBody CompetitorAnalysisRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                competitorAnalysisService.analyze(request)
        ));
    }

    @GetMapping("/tender-entities")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<String>>> getTenderEntities() {
        return ResponseEntity.ok(ApiResponse.success(
                competitorAnalysisService.getTenderEntities()
        ));
    }
}