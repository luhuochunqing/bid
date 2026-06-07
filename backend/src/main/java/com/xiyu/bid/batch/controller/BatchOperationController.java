// Input: batch service and request DTOs
// Output: Batch Operation REST API endpoints
// Pos: Controller/控制器层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.batch.controller;

import com.xiyu.bid.batch.dto.BatchApproveFeesRequest;
import com.xiyu.bid.batch.dto.BatchAssignRequest;
import com.xiyu.bid.batch.dto.BatchClaimRequest;
import com.xiyu.bid.batch.dto.BatchDeleteRequest;
import com.xiyu.bid.batch.dto.BatchOperationResponse;
import com.xiyu.bid.batch.dto.BatchProjectUpdateRequest;
import com.xiyu.bid.batch.dto.BatchTenderAssignRequest;
import com.xiyu.bid.batch.dto.BatchTenderStatusUpdateRequest;
import com.xiyu.bid.batch.service.BatchOperationService;
import com.xiyu.bid.batch.service.BatchTenderAssignmentService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.List;

@RestController
@RequestMapping("/api/batch")
@RequiredArgsConstructor
@Slf4j
public class BatchOperationController {
    private static final String ADMIN_MANAGER_EXPR = "hasAnyRole('ADMIN', 'MANAGER')";
    private static final int ZERO_COUNT = 0;
    private static final int SINGLE_COUNT = 1;

    private final BatchOperationService batchOperationService;
    private final BatchTenderAssignmentService batchTenderAssignmentService;
    private final AuthService authService;
    private static final int MAX_REMARK_LENGTH = 500;

    @PostMapping("/tenders/claim")
    @PreAuthorize(ADMIN_MANAGER_EXPR)
    public ResponseEntity<ApiResponse<BatchOperationResponse>> batchClaimTenders(
            @Valid @RequestBody BatchClaimRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (request.getItemIds() == null || request.getItemIds().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(400, "Item IDs list cannot be empty"));
        }

        User currentUser = getCurrentUser(userDetails);
        log.info("POST /api/batch/tenders/claim - Claiming {} tenders by user: {}",
                request.getItemIds().size(), currentUser.getId());

        BatchOperationResponse response = batchOperationService.batchClaimTenders(
                request.getItemIds(), currentUser.getId());

        String message = buildSuccessMessage("claimed", "tender", response.getSuccessCount());
        return ResponseEntity.ok(ApiResponse.success(message, response));
    }

    @PostMapping("/tasks/assign")
    @PreAuthorize(ADMIN_MANAGER_EXPR)
    public ResponseEntity<ApiResponse<BatchOperationResponse>> batchAssignTasks(
            @Valid @RequestBody BatchAssignRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = getCurrentUser(userDetails);

        log.info("POST /api/batch/tasks/assign - Assigning {} tasks by user: {}",
                request.getTaskIds().size(), currentUser.getId());

        if (request.getTaskIds() == null || request.getTaskIds().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(400, "Task IDs list cannot be empty"));
        }
        if (request.getAssigneeId() == null
                && (request.getAssigneeDeptCode() == null || request.getAssigneeDeptCode().isBlank())
                && (request.getAssigneeRoleCode() == null || request.getAssigneeRoleCode().isBlank())) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(400, "Assignment target cannot be empty"));
        }

        sanitizeRequestRemark(request);
        BatchOperationResponse response = batchOperationService.batchAssignTasks(request, currentUser);

        String message = buildSuccessMessage("assigned", "task", response.getSuccessCount());
        return ResponseEntity.ok(ApiResponse.success(message, response));
    }

    @DeleteMapping("/projects")
    @PreAuthorize(ADMIN_MANAGER_EXPR)
    public ResponseEntity<ApiResponse<BatchOperationResponse>> batchDeleteProjects(
            @Valid @RequestBody BatchDeleteRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = getCurrentUser(userDetails);
        log.info("DELETE /api/batch/projects - Deleting {} projects by user: {}",
                request.getItemIds().size(), currentUser.getId());

        if (request.getItemIds() == null || request.getItemIds().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(400, "Item IDs list cannot be empty"));
        }
        sanitizeRequestReason(request);
        BatchOperationResponse response = batchOperationService.batchDeleteProjects(
                request.getItemIds(),
                currentUser.getId(),
                currentUser.getRole());

        String message = buildSuccessMessage("deleted", "project", response.getSuccessCount());
        return ResponseEntity.ok(ApiResponse.success(message, response));
    }

    @DeleteMapping("/{type}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('MANAGER') and !#type.equalsIgnoreCase('tender'))")
    public ResponseEntity<ApiResponse<BatchOperationResponse>> batchDeleteItems(
            @PathVariable String type,
            @Valid @RequestBody BatchDeleteRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = getCurrentUser(userDetails);

        log.info("DELETE /api/batch/{} - Deleting {} items", type, request.getItemIds().size());

        if (request.getItemIds() == null || request.getItemIds().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(400, "Item IDs list cannot be empty"));
        }

        sanitizeRequestReason(request);

        try {
            BatchOperationResponse response = batchOperationService.batchDeleteItems(
                    type,
                    request.getItemIds(),
                    currentUser.getId(),
                    currentUser.getRole());
            String message = buildSuccessMessage("deleted", type, response.getSuccessCount());
            return ResponseEntity.ok(ApiResponse.success(message, response));

        } catch (IllegalArgumentException e) {
            log.warn("Invalid item type for batch deletion: {}", type);
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(400, "Invalid item type: " + type + ". Supported types: tender, task, project"));
        } catch (RuntimeException e) {
            log.error("Error during batch deletion: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error(500, "Failed to delete items: " + e.getMessage()));
        }
    }

    @GetMapping("/status/{operationId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<BatchOperationResponse>> getBatchOperationStatus(
            @PathVariable String operationId) {

        log.info("GET /api/batch/status/{} - Querying batch operation status", operationId);

        BatchOperationResponse response = BatchOperationResponse.builder()
                .success(true)
                .successCount(0)
                .failureCount(0)
                .totalCount(0)
                .operationType("STATUS_QUERY")
                .build();

        return ResponseEntity.ok(
                ApiResponse.success("Batch operation status query (placeholder)", response));
    }

    @GetMapping("/history")
    @PreAuthorize(ADMIN_MANAGER_EXPR)
    public ResponseEntity<ApiResponse<List<String>>> getBatchOperationHistory(
            @RequestParam(defaultValue = "10") int limit) {

        log.info("GET /api/batch/history - Querying batch operation history, limit={}", limit);
        return ResponseEntity.ok(ApiResponse.success("Batch operation history (placeholder)", List.of()));
    }

    /**
     * Batch update projects endpoint.
     * Allows updating status and/or manager for multiple projects.
     */
    @PatchMapping("/projects")
    @PreAuthorize(ADMIN_MANAGER_EXPR)
    public ResponseEntity<ApiResponse<BatchOperationResponse>> batchUpdateProjects(
            @Valid @RequestBody BatchProjectUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = getCurrentUser(userDetails);
        log.info("PATCH /api/batch/projects - Updating {} projects by user: {}",
                request.getProjectIds().size(), currentUser.getId());

        if (request.getProjectIds() == null || request.getProjectIds().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(400, "Project IDs list cannot be empty"));
        }

        if (!request.hasUpdates()) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(400, "At least one field (status or managerId) must be specified for update"));
        }

        BatchProjectUpdateRequest sanitizedRequest = sanitizeRequestRemark(request);
        BatchOperationResponse response = batchOperationService.batchUpdateProjects(
                sanitizedRequest, currentUser.getId(), currentUser.getRole());

        String message = buildSuccessMessage("updated", "project", response.getSuccessCount());
        return ResponseEntity.ok(ApiResponse.success(message, response));
    }

    /**
     * Batch approve (mark as paid) fees endpoint.
     * Allows marking multiple fee records as paid at once.
     */
    @PostMapping("/fees/approve")
    @PreAuthorize(ADMIN_MANAGER_EXPR)
    public ResponseEntity<ApiResponse<BatchOperationResponse>> batchApproveFees(
            @Valid @RequestBody BatchApproveFeesRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = getCurrentUser(userDetails);
        log.info("POST /api/batch/fees/approve - Approving {} fees by user: {}",
                request.getFeeIds().size(), currentUser.getId());

        if (request.getFeeIds() == null || request.getFeeIds().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(400, "Fee IDs list cannot be empty"));
        }

        // Sanitize paidBy field
        if (request.getPaidBy() != null && !request.getPaidBy().isEmpty()) {
            String sanitizedPaidBy = InputSanitizer.sanitizeString(request.getPaidBy(), 200);
            request = new BatchApproveFeesRequest(
                    request.getFeeIds(),
                    sanitizedPaidBy,
                    currentUser.getId()
            );
        } else {
            request = new BatchApproveFeesRequest(
                    request.getFeeIds(),
                    null,
                    currentUser.getId()
            );
        }

        BatchOperationResponse response = batchOperationService.batchApproveFees(request, currentUser.getId());

        String message = buildSuccessMessage("approved", "fee", response.getSuccessCount());
        return ResponseEntity.ok(ApiResponse.success(message, response));
    }

    @PatchMapping("/tenders/status")
    @PreAuthorize("@tenderAbandonAuthorizer.canUpdate(authentication, #request)")
    public ResponseEntity<ApiResponse<BatchOperationResponse>> batchUpdateTenderStatus(
            @Valid @RequestBody BatchTenderStatusUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        BatchOperationResponse response = batchTenderAssignmentService.batchUpdateStatus(request, currentUser);
        return ResponseEntity.ok(ApiResponse.success(buildSuccessMessage("updated", "tender", response.getSuccessCount()), response));
    }

    @PostMapping("/tenders/assign")
    @PreAuthorize(ADMIN_MANAGER_EXPR)
    public ResponseEntity<ApiResponse<BatchOperationResponse>> batchAssignTenders(
            @Valid @RequestBody BatchTenderAssignRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        BatchOperationResponse response = batchTenderAssignmentService.batchAssign(request, currentUser);
        return ResponseEntity.ok(ApiResponse.success(buildSuccessMessage("assigned", "tender", response.getSuccessCount()), response));
    }

    private String buildSuccessMessage(String action, String itemType, int count) {
        String normalizedItemType = itemType.toLowerCase(Locale.ROOT);
        if (count == ZERO_COUNT) {
            return String.format("No %ss were %s. Check error details for more information.",
                    normalizedItemType, action);
        } else if (count == SINGLE_COUNT) {
            return String.format("Successfully %s 1 %s", action, normalizedItemType);
        } else {
            return String.format("Successfully %s %d %ss", action, count, normalizedItemType);
        }
    }

    private void sanitizeRequestRemark(BatchAssignRequest request) {
        if (request.getRemark() != null && !request.getRemark().isEmpty()) {
            request.setRemark(InputSanitizer.sanitizeString(request.getRemark(), MAX_REMARK_LENGTH));
        }
        if (request.getAssigneeDeptCode() != null) {
            request.setAssigneeDeptCode(InputSanitizer.sanitizeString(request.getAssigneeDeptCode(), 100));
        }
        if (request.getAssigneeDeptName() != null) {
            request.setAssigneeDeptName(InputSanitizer.sanitizeString(request.getAssigneeDeptName(), 100));
        }
        if (request.getAssigneeRoleCode() != null) {
            request.setAssigneeRoleCode(InputSanitizer.sanitizeString(request.getAssigneeRoleCode(), 64));
        }
        if (request.getAssigneeRoleName() != null) {
            request.setAssigneeRoleName(InputSanitizer.sanitizeString(request.getAssigneeRoleName(), 100));
        }
    }

    private BatchProjectUpdateRequest sanitizeRequestRemark(BatchProjectUpdateRequest request) {
        if (request.getRemark() != null && !request.getRemark().isEmpty()) {
            String sanitizedRemark = InputSanitizer.sanitizeString(request.getRemark(), MAX_REMARK_LENGTH);
            return BatchProjectUpdateRequest.builder()
                    .projectIds(request.getProjectIds())
                    .status(request.getStatus())
                    .managerId(request.getManagerId())
                    .remark(sanitizedRemark)
                    .build();
        }
        return request;
    }

    private void sanitizeRequestReason(BatchDeleteRequest request) {
        if (request.getReason() != null && !request.getReason().isEmpty()) {
            request.setReason(InputSanitizer.sanitizeString(request.getReason(), MAX_REMARK_LENGTH));
        }
    }

    private User getCurrentUser(UserDetails userDetails) {
        if (userDetails == null || userDetails.getUsername() == null || userDetails.getUsername().trim().isEmpty()) {
            throw new org.springframework.security.authentication.AuthenticationServiceException(
                    "Authenticated user is required");
        }
        try {
            return authService.resolveUserByUsername(userDetails.getUsername().trim());
        } catch (org.springframework.security.core.userdetails.UsernameNotFoundException ex) {
            throw new org.springframework.security.authentication.AuthenticationServiceException(
                    "Authenticated user not found: " + userDetails.getUsername(), ex);
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<BatchOperationResponse>> handleIllegalArgumentException(
            IllegalArgumentException e) {

        log.error("Illegal argument in batch operation: {}", e.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<BatchOperationResponse>> handleGenericException(RuntimeException e) {
        log.error("Error in batch operation: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiResponse.error(500, "An error occurred during batch operation: " + e.getMessage()));
    }
}
