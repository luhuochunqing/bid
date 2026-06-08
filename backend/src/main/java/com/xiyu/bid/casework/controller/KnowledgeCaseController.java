package com.xiyu.bid.casework.controller;

import com.xiyu.bid.casework.application.service.CaseExportAppService;
import com.xiyu.bid.casework.application.CaseExportExcelAppService;
import com.xiyu.bid.casework.application.CasePrecipitationAppService;
import com.xiyu.bid.casework.application.service.CaseReferenceAppService;
import com.xiyu.bid.casework.application.service.KnowledgeCaseCommandAppService;
import com.xiyu.bid.casework.application.service.KnowledgeCaseQueryAppService;
import com.xiyu.bid.casework.application.service.KnowledgeCaseRecommendAppService;
import com.xiyu.bid.casework.domain.model.KnowledgeCaseMatchScore;
import com.xiyu.bid.casework.dto.CaseExportQuery;
import com.xiyu.bid.casework.dto.CaseReferenceRecordDTO;
import com.xiyu.bid.casework.dto.KnowledgeCaseResponse;
import com.xiyu.bid.casework.infrastructure.KnowledgeCase;
import com.xiyu.bid.service.ProjectAccessScopeService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cases")
@RequiredArgsConstructor
public class KnowledgeCaseController {

    private final KnowledgeCaseQueryAppService queryAppService;
    private final KnowledgeCaseCommandAppService commandAppService;
    private final KnowledgeCaseRecommendAppService recommendAppService;
    private final CaseReferenceAppService caseReferenceAppService;
    private final CasePrecipitationAppService precipitationAppService;
    private final CaseExportAppService caseExportZipAppService;
    private final CaseExportExcelAppService caseExportExcelAppService;
    private final ProjectAccessScopeService projectAccessScopeService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<List<KnowledgeCaseMatchScore>> recommendCases(
            @RequestParam Long projectId,
            @RequestParam(required = false) String scoringItem,
            @RequestParam(required = false) String keyword) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        return ResponseEntity.ok(recommendAppService.recommendForScoringItem(
                projectId, scoringItem, keyword));
    }

    @GetMapping("/recommend/project")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<List<KnowledgeCaseMatchScore>> recommendForProject(
            @RequestParam Long projectId,
            @RequestParam(required = false) String keyword) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        return ResponseEntity.ok(recommendAppService.recommendForProject(projectId, keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<KnowledgeCase> getCaseDetail(@PathVariable Long id) {
        return ResponseEntity.ok(queryAppService.getCaseDetail(id));
    }

    @PostMapping("/{id}/reuse")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<Map<String, Object>> reuseCase(@PathVariable Long id) {
        String userName = resolveCurrentUserName();
        return ResponseEntity.ok(commandAppService.reuseCase(id, userName));
    }

    @PostMapping("/{id}/off-shelf")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<CasePrecipitationAppService.ReadinessResult> getPrecipitationReadiness(
            @RequestParam Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        return ResponseEntity.ok(precipitationAppService.getReadiness(projectId));
    }

    @PostMapping("/precipitate")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Map<String, Object>> triggerPrecipitation(
            @RequestParam Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        precipitationAppService.triggerPrecipitation(projectId);
        return ResponseEntity.ok(Map.of("success", true, "message", "案例沉淀任务已触发，完成后将通过消息通知"));
    }

    @GetMapping("/{id}/references")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<List<CaseReferenceRecordDTO>> getReferenceRecords(@PathVariable Long id) {
        return ResponseEntity.ok(caseReferenceAppService.getReferenceRecords(id));
    }

    @PostMapping("/export-excel")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public void exportCasesAsExcel(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String scoringCategory,
            @RequestParam(required = false) String customerType,
            @RequestParam(required = false) String projectTypes,
            @RequestParam(required = false) String uploadDateFrom,
            @RequestParam(required = false) String uploadDateTo,
            @RequestParam(required = false) String closeDateFrom,
            @RequestParam(required = false) String closeDateTo,
            @RequestParam(required = false) String statuses,
            HttpServletResponse response) throws IOException {

        var result = caseExportExcelAppService.exportCasesAsExcel(
                keyword, scoringCategory, customerType, parseList(projectTypes),
                uploadDateFrom, uploadDateTo, closeDateFrom, closeDateTo, parseList(statuses));

        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + java.net.URLEncoder.encode(result.filename(), java.nio.charset.StandardCharsets.UTF_8) + "\"");
        response.getOutputStream().write(result.data());
        response.getOutputStream().flush();
    }

    @PostMapping("/export-zip")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<byte[]> exportCasesAsZip(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String scoringCategory,
            @RequestParam(required = false) String customerType,
            @RequestParam(required = false) String projectTypes,
            @RequestParam(required = false) String uploadDateFrom,
            @RequestParam(required = false) String uploadDateTo,
            @RequestParam(required = false) String closeDateFrom,
            @RequestParam(required = false) String closeDateTo,
            @RequestParam(required = false) String statuses,
            @RequestParam(required = false) String sortBy) {
        String operatorName = resolveCurrentUserName();
        CaseExportQuery query = new CaseExportQuery(
                keyword, scoringCategory, customerType, parseList(projectTypes),
                parseList(statuses), uploadDateFrom, uploadDateTo,
                closeDateFrom, closeDateTo, sortBy);

        var result = caseExportZipAppService.exportCases(query, operatorName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentLength(result.zipBytes().length);
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + URLEncoder.encode(result.zipFileName(), StandardCharsets.UTF_8) + "\"");

        return new ResponseEntity<>(result.zipBytes(), headers, HttpStatus.OK);
    }


    private List<String> parseList(String value) {
        if (value == null || value.trim().isEmpty()) return List.of();
        return List.of(value.trim().split(","));
    }

    private String resolveCurrentUserName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            return auth.getName();
        }
        return "未知用户";
    }
}
