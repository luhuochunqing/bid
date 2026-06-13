package com.xiyu.bid.batch.controller;

import com.xiyu.bid.batch.dto.*;
import com.xiyu.bid.batch.service.BatchOperationService;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.service.AuthService;
import com.xiyu.bid.util.InputSanitizer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/batch")
@RequiredArgsConstructor
@Slf4j
public class BatchOperationController {

    private static final String ADMIN_MANAGER_EXPR = "hasAnyRole('ADMIN', 'MANAGER')";
    private static final int MAX_REMARK_LENGTH = 500;

    private final BatchOperationService batchOperationService;
    private final AuthService authService;

    @PostMapping("/tasks/assign")
    @PreAuthorize(ADMIN_MANAGER_EXPR)
    public ResponseEntity<ApiResponse<BatchOperationResponse>> batchAssignTasks(
            @Valid @RequestBody BatchAssignRequest request, @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = resolveUser(userDetails);
        if (request.getTaskIds() == null || request.getTaskIds().isEmpty()) return ResponseEntity.badRequest().body(ApiResponse.error(400, "Task IDs list cannot be empty"));
        if (request.getAssigneeId() == null && (request.getAssigneeDeptCode() == null || request.getAssigneeDeptCode().isBlank()) && (request.getAssigneeRoleCode() == null || request.getAssigneeRoleCode().isBlank())) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Assignment target cannot be empty"));
        }
        sanitizeAssignRemark(request);
        BatchOperationResponse response = batchOperationService.batchAssignTasks(request, currentUser);
        return ResponseEntity.ok(ApiResponse.success(buildMessage("assigned", "task", response.getSuccessCount()), response));
    }

    @DeleteMapping("/projects")
    @PreAuthorize(ADMIN_MANAGER_EXPR)
    public ResponseEntity<ApiResponse<BatchOperationResponse>> batchDeleteProjects(
            @Valid @RequestBody BatchDeleteRequest request, @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = resolveUser(userDetails);
        if (request.getItemIds() == null || request.getItemIds().isEmpty()) return ResponseEntity.badRequest().body(ApiResponse.error(400, "Item IDs list cannot be empty"));
        sanitizeReason(request);
        BatchOperationResponse response = batchOperationService.batchDeleteProjects(request.getItemIds(), currentUser.getId(), currentUser.getRole());
        return ResponseEntity.ok(ApiResponse.success(buildMessage("deleted", "project", response.getSuccessCount()), response));
    }

    @DeleteMapping("/{type}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('MANAGER') and !#type.equalsIgnoreCase('tender'))")
    public ResponseEntity<ApiResponse<BatchOperationResponse>> batchDeleteItems(
            @PathVariable String type, @Valid @RequestBody BatchDeleteRequest request, @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = resolveUser(userDetails);
        if (request.getItemIds() == null || request.getItemIds().isEmpty()) return ResponseEntity.badRequest().body(ApiResponse.error(400, "Item IDs list cannot be empty"));
        sanitizeReason(request);
        try {
            BatchOperationResponse response = batchOperationService.batchDeleteItems(type, request.getItemIds(), currentUser.getId(), currentUser.getRole());
            return ResponseEntity.ok(ApiResponse.success(buildMessage("deleted", type, response.getSuccessCount()), response));
        } catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(ApiResponse.error(400, "Invalid item type: " + type)); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(500, "Failed: " + e.getMessage())); }
    }

    @GetMapping("/status/{operationId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<BatchOperationResponse>> getBatchOperationStatus(@PathVariable String operationId) {
        return ResponseEntity.ok(ApiResponse.success("Status query (placeholder)", BatchOperationResponse.builder().success(true).successCount(0).failureCount(0).totalCount(0).operationType("STATUS_QUERY").build()));
    }

    @GetMapping("/history")
    @PreAuthorize(ADMIN_MANAGER_EXPR)
    public ResponseEntity<ApiResponse<List<String>>> getBatchOperationHistory(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.success("History (placeholder)", List.of()));
    }

    @PatchMapping("/projects")
    @PreAuthorize(ADMIN_MANAGER_EXPR)
    public ResponseEntity<ApiResponse<BatchOperationResponse>> batchUpdateProjects(
            @Valid @RequestBody BatchProjectUpdateRequest request, @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = resolveUser(userDetails);
        if (request.getProjectIds() == null || request.getProjectIds().isEmpty()) return ResponseEntity.badRequest().body(ApiResponse.error(400, "Project IDs list cannot be empty"));
        if (!request.hasUpdates()) return ResponseEntity.badRequest().body(ApiResponse.error(400, "At least one field must be specified"));
        BatchProjectUpdateRequest sanitized = sanitizeProjectRemark(request);
        BatchOperationResponse response = batchOperationService.batchUpdateProjects(sanitized, currentUser.getId(), currentUser.getRole());
        return ResponseEntity.ok(ApiResponse.success(buildMessage("updated", "project", response.getSuccessCount()), response));
    }

    @PostMapping("/fees/approve")
    @PreAuthorize(ADMIN_MANAGER_EXPR)
    public ResponseEntity<ApiResponse<BatchOperationResponse>> batchApproveFees(
            @Valid @RequestBody BatchApproveFeesRequest request, @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = resolveUser(userDetails);
        if (request.getFeeIds() == null || request.getFeeIds().isEmpty()) return ResponseEntity.badRequest().body(ApiResponse.error(400, "Fee IDs list cannot be empty"));
        if (request.getPaidBy() != null && !request.getPaidBy().isEmpty()) {
            request = new BatchApproveFeesRequest(request.getFeeIds(), InputSanitizer.sanitizeString(request.getPaidBy(), 200), currentUser.getId());
        } else {
            request = new BatchApproveFeesRequest(request.getFeeIds(), null, currentUser.getId());
        }
        BatchOperationResponse response = batchOperationService.batchApproveFees(request, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(buildMessage("approved", "fee", response.getSuccessCount()), response));
    }

    private String buildMessage(String action, String type, int count) {
        if (count == 0) return String.format("No %ss were %s.", type, action);
        return String.format("Successfully %s %d %ss", action, count, type);
    }

    private void sanitizeAssignRemark(BatchAssignRequest r) {
        if (r.getRemark() != null && !r.getRemark().isEmpty()) r.setRemark(InputSanitizer.sanitizeString(r.getRemark(), MAX_REMARK_LENGTH));
        if (r.getAssigneeDeptCode() != null) r.setAssigneeDeptCode(InputSanitizer.sanitizeString(r.getAssigneeDeptCode(), 100));
        if (r.getAssigneeDeptName() != null) r.setAssigneeDeptName(InputSanitizer.sanitizeString(r.getAssigneeDeptName(), 100));
        if (r.getAssigneeRoleCode() != null) r.setAssigneeRoleCode(InputSanitizer.sanitizeString(r.getAssigneeRoleCode(), 64));
        if (r.getAssigneeRoleName() != null) r.setAssigneeRoleName(InputSanitizer.sanitizeString(r.getAssigneeRoleName(), 100));
    }

    private BatchProjectUpdateRequest sanitizeProjectRemark(BatchProjectUpdateRequest r) {
        if (r.getRemark() != null && !r.getRemark().isEmpty()) {
            return BatchProjectUpdateRequest.builder().projectIds(r.getProjectIds()).status(r.getStatus()).managerId(r.getManagerId()).remark(InputSanitizer.sanitizeString(r.getRemark(), MAX_REMARK_LENGTH)).build();
        }
        return r;
    }

    private void sanitizeReason(BatchDeleteRequest r) { if (r.getReason() != null && !r.getReason().isEmpty()) r.setReason(InputSanitizer.sanitizeString(r.getReason(), MAX_REMARK_LENGTH)); }

    private User resolveUser(UserDetails userDetails) {
        if (userDetails == null || userDetails.getUsername() == null || userDetails.getUsername().trim().isEmpty()) throw new org.springframework.security.authentication.AuthenticationServiceException("Authenticated user is required");
        return authService.resolveUserByUsername(userDetails.getUsername().trim());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<BatchOperationResponse>> handleIllegalArgument(IllegalArgumentException e) { return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage())); }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<BatchOperationResponse>> handleGeneric(RuntimeException e) { log.error("Batch error: {}", e.getMessage(), e); return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(500, e.getMessage())); }
}
