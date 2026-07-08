package com.xiyu.bid.casework.controller;

import com.xiyu.bid.casework.application.BidCaseSliceDetail;
import com.xiyu.bid.casework.application.CaseSliceJsonlImporter;
import com.xiyu.bid.casework.application.service.BatchEmbeddingAppService;
import com.xiyu.bid.casework.application.service.BidCaseSliceRecommendAppService;
import com.xiyu.bid.casework.domain.model.BidCaseSliceRecommendation;
import com.xiyu.bid.casework.infrastructure.BidCaseSlice;
import com.xiyu.bid.casework.infrastructure.BidCaseSliceRepository;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

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
    private final CaseSliceJsonlImporter jsonlImporter;
    private final BidCaseSliceRepository sliceRepository;

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
        int size = batchSize != null ? batchSize : BatchEmbeddingAppService.DEFAULT_BATCH_SIZE;
        BatchEmbeddingAppService.EmbeddingResult result = batchEmbeddingAppService.embedAllUnprocessed(size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/admin/import")
    @PreAuthorize("hasAuthority('" + RoleProfileCatalog.SYSTEM_ADMIN_PERMISSION + "')")
    public ResponseEntity<ApiResponse<CaseSliceJsonlImporter.ImportResult>> importFromJsonl() {
        CaseSliceJsonlImporter.ImportResult result = jsonlImporter.importAll();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/admin/import/slice")
    @PreAuthorize("hasAuthority('" + RoleProfileCatalog.SYSTEM_ADMIN_PERMISSION + "')")
    public ResponseEntity<ApiResponse<BidCaseSlice>> importSingleSlice(@RequestBody Map<String, Object> payload) {
        String project = (String) payload.get("project");
        String docxFile = (String) payload.get("docx_file");
        String docxLabel = (String) payload.get("docx_label");
        String title = (String) payload.get("title");
        String textPreview = (String) payload.get("text_preview");
        
        if (project == null || project.isBlank()) {
            throw new IllegalArgumentException("project 不能为空");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title 不能为空");
        }
        
        BidCaseSlice slice = new BidCaseSlice();
        slice.setProjectDir(project);
        slice.setProjectIdx((Integer) payload.getOrDefault("project_idx", 0));
        slice.setDocxFile(docxFile != null ? docxFile : "");
        slice.setDocxLabel(docxLabel != null ? docxLabel : "其他");
        slice.setSectionIdx((Integer) payload.getOrDefault("section_idx", 0));
        slice.setLevel((Integer) payload.getOrDefault("level", 1));
        slice.setTitle(title);
        slice.setTextPreview(textPreview != null ? textPreview : "");
        slice.setTextLength((Integer) payload.getOrDefault("text_length", 0));
        slice.setParaCount((Integer) payload.getOrDefault("para_count", 0));
        
        BidCaseSlice saved = sliceRepository.save(slice);
        return ResponseEntity.ok(ApiResponse.success(saved));
    }

    @GetMapping("/admin/stats")
    @PreAuthorize("hasAuthority('" + RoleProfileCatalog.SYSTEM_ADMIN_PERMISSION + "')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        long total = sliceRepository.count();
        long withEmbedding = sliceRepository.countByEmbeddingIsNotNull();
        long withoutEmbedding = total - withEmbedding;
        
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "total", total,
                "withEmbedding", withEmbedding,
                "withoutEmbedding", withoutEmbedding
        )));
    }

    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasAuthority('" + RoleProfileCatalog.SYSTEM_ADMIN_PERMISSION + "')")
    public ResponseEntity<ApiResponse<Void>> deleteSlice(@PathVariable Long id) {
        if (!sliceRepository.existsById(id)) {
            throw new IllegalArgumentException("切片不存在: " + id);
        }
        sliceRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private void requireNonNull(Long value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }
}
