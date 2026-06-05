package com.xiyu.bid.projecttenderbreakdown.controller;

import com.xiyu.bid.biddraftagent.application.BidTenderDocumentImportAppService;
import com.xiyu.bid.biddraftagent.application.BidUploadedTenderDocumentReuseAppService;
import com.xiyu.bid.biddraftagent.application.TenderBreakdownReadiness;
import com.xiyu.bid.biddraftagent.dto.BidTenderDocumentParseDTO;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.projecttenderbreakdown.application.ProjectTenderBreakdownReadinessService;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/projects/{projectId}/tender-breakdown")
@RequiredArgsConstructor
public class ProjectTenderBreakdownController {

    private final ProjectAccessScopeService projectAccessScopeService;
    private final BidTenderDocumentImportAppService importAppService;
    private final BidUploadedTenderDocumentReuseAppService uploadedReuseAppService;
    private final ProjectTenderBreakdownReadinessService readinessService;

    @GetMapping("/readiness")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<TenderBreakdownReadiness>> readiness(@PathVariable Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        return ResponseEntity.ok(ApiResponse.success(readinessService.readiness(projectId)));
    }

    @GetMapping("/latest")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<BidTenderDocumentParseDTO>> latestParsedTenderBreakdown(
            @PathVariable Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        return importAppService.latestParsedTenderDocument(projectId)
                .map(result -> ResponseEntity.ok(ApiResponse.success(result.getMessage(), result)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.success("尚未解析招标文件", null)));
    }

    @PostMapping("/reuse-uploaded")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<BidTenderDocumentParseDTO>> reuseUploadedTenderBreakdown(
            @PathVariable Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        return uploadedReuseAppService.parseLatestUploadedTenderDocument(projectId)
                .map(result -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success(result.getMessage(), result)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.success("尚未找到可复用的已上传招标文件", null)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<BidTenderDocumentParseDTO>> parseTenderBreakdown(
            @PathVariable Long projectId,
            @RequestParam("file") MultipartFile file) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        BidTenderDocumentParseDTO result = importAppService.parseTenderDocument(projectId, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result.getMessage(), result));
    }
}
