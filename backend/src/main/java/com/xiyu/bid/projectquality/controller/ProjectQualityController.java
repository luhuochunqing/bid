package com.xiyu.bid.projectquality.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.projectquality.dto.ProjectQualityCheckResponse;
import com.xiyu.bid.projectquality.service.ProjectQualityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/quality-checks")
@RequiredArgsConstructor
public class ProjectQualityController {

    private final ProjectQualityService projectQualityService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<ProjectQualityCheckResponse>> runQualityCheck(@PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.success(projectQualityService.runQualityCheck(projectId)));
    }

    @GetMapping("/latest")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<ProjectQualityCheckResponse>> getLatest(@PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.success(projectQualityService.getLatest(projectId)));
    }

    @PostMapping("/{checkId}/issues/{issueId}/adopt")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<ProjectQualityCheckResponse>> adoptIssue(
            @PathVariable Long projectId,
            @PathVariable Long checkId,
            @PathVariable Long issueId) {
        return ResponseEntity.ok(ApiResponse.success(projectQualityService.adoptIssue(projectId, checkId, issueId)));
    }

    @PostMapping("/{checkId}/issues/{issueId}/ignore")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<ProjectQualityCheckResponse>> ignoreIssue(
            @PathVariable Long projectId,
            @PathVariable Long checkId,
            @PathVariable Long issueId) {
        return ResponseEntity.ok(ApiResponse.success(projectQualityService.ignoreIssue(projectId, checkId, issueId)));
    }
}
