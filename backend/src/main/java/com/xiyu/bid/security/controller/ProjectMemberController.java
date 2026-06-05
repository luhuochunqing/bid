package com.xiyu.bid.security.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.dto.ProjectMemberDTO;
import com.xiyu.bid.dto.ProjectMemberRequest;
import com.xiyu.bid.security.service.ProjectMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ProjectMemberService is the single source of truth for project access checks;
 * the controller delegates to it so we don't pay the `getAllowedProjectIds` cost twice.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/members")
@RequiredArgsConstructor
@Slf4j
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<ProjectMemberDTO>>> getMembers(@PathVariable Long projectId) {
        log.info("GET /api/projects/{}/members - Fetching project members", projectId);
        return ResponseEntity.ok(ApiResponse.success(projectMemberService.getProjectMembers(projectId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ProjectMemberDTO>> addMember(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectMemberRequest request) {
        log.info("POST /api/projects/{}/members - Adding member", projectId);
        return ResponseEntity.ok(ApiResponse.success(
                "Member added successfully",
                projectMemberService.addProjectMember(projectId, request)));
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable Long projectId,
            @PathVariable Long userId) {
        log.info("DELETE /api/projects/{}/members/{} - Removing member", projectId, userId);
        projectMemberService.removeProjectMember(projectId, userId);
        return ResponseEntity.ok(ApiResponse.success("Member removed successfully", null));
    }
}
