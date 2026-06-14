package com.xiyu.bid.batch.controller;

import com.xiyu.bid.batch.dto.*;
import com.xiyu.bid.batch.service.BatchOperationService;
import com.xiyu.bid.batch.service.BatchTenderAssignmentService;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/batch/tenders")
@RequiredArgsConstructor
@Slf4j
public class BatchTenderController {

    private final BatchOperationService batchOperationService;
    private final BatchTenderAssignmentService batchTenderAssignmentService;
    private final AuthService authService;

    @PostMapping("/claim")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<BatchOperationResponse>> batchClaimTenders(
            @Valid @RequestBody BatchClaimRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (request.getItemIds() == null || request.getItemIds().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Item IDs list cannot be empty"));
        }
        User currentUser = resolveUser(userDetails);
        log.info("POST /api/batch/tenders/claim - Claiming {} tenders by user: {}", request.getItemIds().size(), currentUser.getId());
        BatchOperationResponse response = batchOperationService.batchClaimTenders(request.getItemIds(), currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(buildMessage("claimed", "tender", response.getSuccessCount()), response));
    }

    @PatchMapping("/status")
    @PreAuthorize("@tenderAbandonAuthorizer.canUpdate(authentication, #request)")
    public ResponseEntity<ApiResponse<BatchOperationResponse>> batchUpdateTenderStatus(
            @Valid @RequestBody BatchTenderStatusUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = resolveUser(userDetails);
        BatchOperationResponse response = batchTenderAssignmentService.batchUpdateStatus(request, currentUser);
        return ResponseEntity.ok(ApiResponse.success(buildMessage("updated", "tender", response.getSuccessCount()), response));
    }

    @PostMapping("/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<BatchOperationResponse>> batchAssignTenders(
            @Valid @RequestBody BatchTenderAssignRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = resolveUser(userDetails);
        BatchOperationResponse response = batchTenderAssignmentService.batchAssign(request, currentUser);
        return ResponseEntity.ok(ApiResponse.success(buildMessage("assigned", "tender", response.getSuccessCount()), response));
    }

    private String buildMessage(String action, String type, int count) {
        if (count == 0) return String.format("No %ss were %s.", type, action);
        return String.format("Successfully %s %d %ss", action, count, type);
    }

    private User resolveUser(UserDetails userDetails) {
        if (userDetails == null || userDetails.getUsername() == null || userDetails.getUsername().trim().isEmpty()) {
            throw new org.springframework.security.authentication.AuthenticationServiceException("Authenticated user is required");
        }
        try {
            return authService.resolveUserByUsername(userDetails.getUsername().trim());
        } catch (org.springframework.security.core.userdetails.UsernameNotFoundException ex) {
            throw new org.springframework.security.authentication.AuthenticationServiceException("Authenticated user not found", ex);
        }
    }
}
