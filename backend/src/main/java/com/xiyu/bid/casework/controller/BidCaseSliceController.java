package com.xiyu.bid.casework.controller;

import com.xiyu.bid.casework.application.BidCaseSliceDetail;
import com.xiyu.bid.casework.application.service.BatchEmbeddingAppService;
import com.xiyu.bid.casework.application.service.BidCaseSliceRecommendAppService;
import com.xiyu.bid.casework.domain.model.BidCaseSliceRecommendation;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 案例切片语义检索 API。
 */
@RestController
@RequestMapping("/api/case-slices")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class BidCaseSliceController {

    private final BidCaseSliceRecommendAppService recommendAppService;
    private final BatchEmbeddingAppService batchEmbeddingAppService;
    private final ProjectAccessScopeService projectAccessScopeService;

    @GetMapping("/recommend")
    public ResponseEntity<ApiResponse<List<BidCaseSliceRecommendation>>> recommendByScoringItem(
            @RequestParam Long projectId,
            @RequestParam Long scoringItemId,
            @RequestParam(required = false) Integer topK) {

        requireNonNull(projectId, "projectId 不能为空");
        requireNonNull(scoringItemId, "scoringItemId 不能为空");

        List<BidCaseSliceRecommendation> result = recommendAppService.recommendByScoringItem(
                projectId, scoringItemId, topK);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/recommend/by-query")
    @PreAuthorize("hasAuthority('" + RoleProfileCatalog.SYSTEM_ADMIN_PERMISSION + "')")
    public ResponseEntity<ApiResponse<List<BidCaseSliceRecommendation>>> recommendByQuery(
            @RequestParam String query,
            @RequestParam(required = false) Integer topK) {

        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query 不能为空");
        }

        List<BidCaseSliceRecommendation> result = recommendAppService.recommendByQuery(query, topK);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BidCaseSliceDetail>> getSliceDetail(
            @PathVariable Long id,
            @RequestParam Long projectId) {
        requireNonNull(projectId, "projectId 不能为空");
        BidCaseSliceDetail detail = recommendAppService.getSliceDetail(id, projectId);
        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    @PostMapping("/admin/batch-embed")
    @PreAuthorize("hasAuthority('" + RoleProfileCatalog.SYSTEM_ADMIN_PERMISSION + "')")
    public ResponseEntity<ApiResponse<BatchEmbeddingAppService.EmbeddingResult>> batchEmbed(
            @RequestParam(required = false) Integer batchSize) {
        BatchEmbeddingAppService.EmbeddingResult result = batchEmbeddingAppService.embedAllUnprocessed(batchSize);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private void requireNonNull(Long value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }
}
