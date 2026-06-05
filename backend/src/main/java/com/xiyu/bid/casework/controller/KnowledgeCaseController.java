package com.xiyu.bid.casework.controller;

import com.xiyu.bid.casework.application.CasePrecipitationAppService;
import com.xiyu.bid.casework.application.service.KnowledgeCaseCommandAppService;
import com.xiyu.bid.casework.application.service.KnowledgeCaseQueryAppService;
import com.xiyu.bid.casework.application.service.KnowledgeCaseRecommendAppService;
import com.xiyu.bid.casework.domain.model.KnowledgeCaseMatchScore;
import com.xiyu.bid.casework.dto.KnowledgeCaseResponse;
import com.xiyu.bid.casework.infrastructure.KnowledgeCase;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cases")
@RequiredArgsConstructor
public class KnowledgeCaseController {

    private final KnowledgeCaseQueryAppService queryAppService;
    private final KnowledgeCaseCommandAppService commandAppService;
    private final KnowledgeCaseRecommendAppService recommendAppService;
    private final CasePrecipitationAppService precipitationAppService;

    @GetMapping
    public ResponseEntity<Page<KnowledgeCaseResponse>> queryCases(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String scoringCategory,
            @RequestParam(required = false) String customerType,
            @RequestParam(required = false) String projectTypes,
            @RequestParam(required = false) String uploadDateFrom,
            @RequestParam(required = false) String uploadDateTo,
            @RequestParam(required = false) String closeDateFrom,
            @RequestParam(required = false) String closeDateTo,
            @RequestParam(required = false) String statuses,
            @RequestParam(defaultValue = "created") String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(queryAppService.queryCases(
                keyword, scoringCategory, customerType, parseList(projectTypes),
                uploadDateFrom, uploadDateTo, closeDateFrom, closeDateTo,
                parseList(statuses), sortBy, page, size));
    }

    @GetMapping("/recommend")
    public ResponseEntity<List<KnowledgeCaseMatchScore>> recommendCases(
            @RequestParam Long projectId,
            @RequestParam(required = false) String scoringItem,
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(recommendAppService.recommendForScoringItem(
                projectId, scoringItem, keyword));
    }

    @GetMapping("/recommend/project")
    public ResponseEntity<List<KnowledgeCaseMatchScore>> recommendForProject(
            @RequestParam Long projectId,
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(recommendAppService.recommendForProject(projectId, keyword));
    }

    @GetMapping("/{id}")
    public ResponseEntity<KnowledgeCase> getCaseDetail(@PathVariable Long id) {
        return ResponseEntity.ok(queryAppService.getCaseDetail(id));
    }

    @PostMapping("/{id}/reuse")
    public ResponseEntity<Map<String, Object>> reuseCase(@PathVariable Long id) {
        return ResponseEntity.ok(commandAppService.reuseCase(id));
    }

    @PostMapping("/{id}/off-shelf")
    public ResponseEntity<Map<String, Object>> offShelfCase(@PathVariable Long id) {
        return ResponseEntity.ok(commandAppService.offShelfCase(id));
    }

    @PostMapping("/{id}/pin")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Map<String, Object>> pinCase(@PathVariable Long id) {
        return ResponseEntity.ok(commandAppService.pinCase(id));
    }

    @PostMapping("/{id}/unpin")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Map<String, Object>> unpinCase(@PathVariable Long id) {
        return ResponseEntity.ok(commandAppService.unpinCase(id));
    }

    @GetMapping("/precipitation-readiness")
    public ResponseEntity<CasePrecipitationAppService.ReadinessResult> getPrecipitationReadiness(
            @RequestParam Long projectId) {
        return ResponseEntity.ok(precipitationAppService.getReadiness(projectId));
    }

    @PostMapping("/precipitate")
    public ResponseEntity<Map<String, Object>> triggerPrecipitation(
            @RequestParam Long projectId) {
        precipitationAppService.triggerPrecipitation(projectId);
        return ResponseEntity.ok(Map.of("success", true, "message", "案例沉淀任务已触发，完成后将通过消息通知"));
    }


    private List<String> parseList(String value) {
        if (value == null || value.trim().isEmpty()) return List.of();
        return List.of(value.trim().split(","));
    }
}
