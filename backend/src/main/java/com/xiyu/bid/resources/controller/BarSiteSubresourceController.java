// Input: resources service and request DTOs
// Output: Bar Site Subresource REST API endpoints
// Pos: Controller/控制器层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.resources.controller;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.resources.dto.BarAssetResponseDTO;
import com.xiyu.bid.resources.dto.BarSiteAccountDTO;
import com.xiyu.bid.resources.dto.BarSiteAccountRequest;
import com.xiyu.bid.resources.dto.BarSiteAttachmentCreateRequest;
import com.xiyu.bid.resources.dto.BarSiteAttachmentDTO;
import com.xiyu.bid.resources.dto.BarSiteSopRequest;
import com.xiyu.bid.resources.dto.BarSiteStatusUpdateRequest;
import com.xiyu.bid.resources.dto.BarSiteVerificationDTO;
import com.xiyu.bid.resources.dto.BarSiteVerificationRequest;
import com.xiyu.bid.resources.service.BarSiteSubresourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/resources/bar-assets/{assetId}")
@RequiredArgsConstructor
public class BarSiteSubresourceController {

    private final BarSiteSubresourceService barSiteSubresourceService;

    @GetMapping("/accounts")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<BarSiteAccountDTO>>> getAccounts(@PathVariable Long assetId) {
        return ResponseEntity.ok(ApiResponse.success(barSiteSubresourceService.getAccounts(assetId)));
    }

    @PostMapping("/accounts")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "CREATE", entityType = "BarSiteAccount", description = "Create BAR site account")
    public ResponseEntity<ApiResponse<BarSiteAccountDTO>> createAccount(
            @PathVariable Long assetId,
            @Valid @RequestBody BarSiteAccountRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Account created successfully",
                barSiteSubresourceService.createAccount(assetId, request)));
    }

    @PutMapping("/accounts/{accountId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "UPDATE", entityType = "BarSiteAccount", description = "Update BAR site account")
    public ResponseEntity<ApiResponse<BarSiteAccountDTO>> updateAccount(
            @PathVariable Long assetId,
            @PathVariable Long accountId,
            @Valid @RequestBody BarSiteAccountRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Account updated successfully",
                barSiteSubresourceService.updateAccount(assetId, accountId, request)));
    }

    @DeleteMapping("/accounts/{accountId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "DELETE", entityType = "BarSiteAccount", description = "Delete BAR site account")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(@PathVariable Long assetId, @PathVariable Long accountId) {
        barSiteSubresourceService.deleteAccount(assetId, accountId);
        return ResponseEntity.ok(ApiResponse.success("Account deleted successfully", null));
    }

    @PatchMapping("/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "UPDATE", entityType = "BarAsset", description = "Update BAR site status")
    public ResponseEntity<ApiResponse<BarAssetResponseDTO>> updateStatus(
            @PathVariable Long assetId,
            @Valid @RequestBody BarSiteStatusUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Status updated successfully",
                barSiteSubresourceService.updateStatus(assetId, request)));
    }

    @PostMapping("/verify")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "VERIFY", entityType = "BarAsset", description = "Verify BAR site")
    public ResponseEntity<ApiResponse<BarSiteVerificationDTO>> verify(
            @PathVariable Long assetId,
            @RequestBody(required = false) BarSiteVerificationRequest request) {
        BarSiteVerificationRequest safeRequest = request != null ? request : new BarSiteVerificationRequest();
        return ResponseEntity.ok(ApiResponse.success("Site verified successfully",
                barSiteSubresourceService.verify(assetId, safeRequest)));
    }

    @GetMapping("/verification-records")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<BarSiteVerificationDTO>>> getVerificationRecords(@PathVariable Long assetId) {
        return ResponseEntity.ok(ApiResponse.success(barSiteSubresourceService.getVerificationRecords(assetId)));
    }

    @GetMapping("/sop")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<BarSiteSopRequest>> getSop(@PathVariable Long assetId) {
        return ResponseEntity.ok(ApiResponse.success(barSiteSubresourceService.getSop(assetId)));
    }

    @PutMapping("/sop")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "UPDATE", entityType = "BarSiteSop", description = "Update BAR site SOP")
    public ResponseEntity<ApiResponse<BarSiteSopRequest>> updateSop(
            @PathVariable Long assetId,
            @RequestBody BarSiteSopRequest request) {
        return ResponseEntity.ok(ApiResponse.success("SOP updated successfully",
                barSiteSubresourceService.upsertSop(assetId, request)));
    }

    @GetMapping("/attachments")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<BarSiteAttachmentDTO>>> getAttachments(@PathVariable Long assetId) {
        return ResponseEntity.ok(ApiResponse.success(barSiteSubresourceService.getAttachments(assetId)));
    }

    @PostMapping("/attachments")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "CREATE", entityType = "BarSiteAttachment", description = "Create BAR site attachment")
    public ResponseEntity<ApiResponse<BarSiteAttachmentDTO>> createAttachment(
            @PathVariable Long assetId,
            @Valid @RequestBody BarSiteAttachmentCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Attachment created successfully",
                barSiteSubresourceService.createAttachment(assetId, request)));
    }

    @DeleteMapping("/attachments/{attachmentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "DELETE", entityType = "BarSiteAttachment", description = "Delete BAR site attachment")
    public ResponseEntity<ApiResponse<Void>> deleteAttachment(@PathVariable Long assetId, @PathVariable Long attachmentId) {
        barSiteSubresourceService.deleteAttachment(assetId, attachmentId);
        return ResponseEntity.ok(ApiResponse.success("Attachment deleted successfully", null));
    }
}
