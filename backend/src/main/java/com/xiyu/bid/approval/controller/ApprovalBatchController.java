package com.xiyu.bid.approval.controller;

import com.xiyu.bid.approval.service.ApprovalWorkflowService;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.approval.service.ApprovalCurrentUserLookupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@Tag(name = "审批批量操作", description = "批量审批通过/驳回")
@RequestMapping("/api/approvals/batch")
@RequiredArgsConstructor
public class ApprovalBatchController {

    private final ApprovalWorkflowService approvalWorkflowService;
    private final ApprovalCurrentUserLookupService currentUserLookupService;

    @PostMapping("/approve")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "批量审批通过")
    public ResponseEntity<ApiResponse<Map<UUID, String>>> batchApprove(
            @RequestBody Map<String, Object> requestBody,
            @AuthenticationPrincipal UserDetails userDetails) {
        @SuppressWarnings("unchecked") List<UUID> ids = (List<UUID>) requestBody.get("ids");
        String comment = (String) requestBody.getOrDefault("comment", "批量通过");
        User user = currentUserLookupService.requireUser(userDetails);
        return ResponseEntity.ok(ApiResponse.success(approvalWorkflowService.batchApprove(ids, user.getId(), user.getUsername(), comment)));
    }

    @PostMapping("/reject")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "批量驳回")
    public ResponseEntity<ApiResponse<Map<UUID, String>>> batchReject(
            @RequestBody Map<String, Object> requestBody,
            @AuthenticationPrincipal UserDetails userDetails) {
        @SuppressWarnings("unchecked") List<UUID> ids = (List<UUID>) requestBody.get("ids");
        String comment = (String) requestBody.getOrDefault("comment", "批量驳回");
        User user = currentUserLookupService.requireUser(userDetails);
        return ResponseEntity.ok(ApiResponse.success(approvalWorkflowService.batchReject(ids, user.getId(), user.getUsername(), comment)));
    }
}
