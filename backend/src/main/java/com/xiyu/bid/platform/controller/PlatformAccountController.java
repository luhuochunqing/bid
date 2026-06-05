// Input: PlatformAccountService, DTOs
// Output: REST API Endpoints
// Pos: Controller/控制器层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.platform.controller;

import com.xiyu.bid.audit.service.IAuditLogService;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.platform.dto.BorrowAccountRequest;
import com.xiyu.bid.platform.dto.PlatformAccountCreateRequest;
import com.xiyu.bid.platform.dto.PlatformAccountDTO;
import com.xiyu.bid.platform.dto.PlatformAccountStatisticsDTO;
import com.xiyu.bid.platform.service.PlatformAccountService;
import com.xiyu.bid.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** REST Controller for Platform Account Management. */
@RestController
@RequestMapping("/api/platform/accounts")
@RequiredArgsConstructor
@Slf4j
public class PlatformAccountController {

    /** Platform account business logic. */
    private final PlatformAccountService platformAccountService;
    /** Authentication service. */
    private final AuthService authService;
    /** Audit log service. */
    private final IAuditLogService auditLogService;

    /**
     * Create a new platform account
     * POST /api/platform/accounts
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<PlatformAccountDTO> createAccount(
        @Valid @RequestBody PlatformAccountCreateRequest request,
        @AuthenticationPrincipal User currentUser) {

        log.info("Creating platform account: {}", request.getAccountName());
        PlatformAccountDTO created = platformAccountService.createAccount(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Get all platform accounts
     * GET /api/platform/accounts
     */
    @GetMapping
    public ResponseEntity<List<PlatformAccountDTO>> getAllAccounts() {
        log.debug("Fetching all platform accounts");
        List<PlatformAccountDTO> accounts = platformAccountService.getAllAccounts();
        return ResponseEntity.ok(accounts);
    }

    /**
     * Get platform account by ID
     * GET /api/platform/accounts/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<PlatformAccountDTO> getAccountById(@PathVariable Long id) {
        log.debug("Fetching platform account with id: {}", id);
        PlatformAccountDTO account = platformAccountService.getAccountById(id);
        return ResponseEntity.ok(account);
    }

    /** Get operation logs for an account. */
    @GetMapping("/{id}/logs")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<com.xiyu.bid.audit.dto.AuditLogQueryResponse>> getAccountLogs(
            @PathVariable final Long id) {
        var logs = auditLogService.queryLogs(null, null, "PlatformAccount",
                null, null, null, null);
        return ResponseEntity.ok(ApiResponse.success("Success", logs));
    }

    /**
     * Update platform account
     * PUT /api/platform/accounts/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<PlatformAccountDTO> updateAccount(
        @PathVariable Long id,
        @Valid @RequestBody PlatformAccountCreateRequest request,
        @AuthenticationPrincipal User currentUser) {

        log.info("Updating platform account with id: {}", id);
        PlatformAccountDTO updated = platformAccountService.updateAccount(id, request, currentUser);
        return ResponseEntity.ok(updated);
    }

    /**
     * Delete platform account
     * DELETE /api/platform/accounts/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteAccount(
        @PathVariable Long id,
        @AuthenticationPrincipal User currentUser) {

        log.info("Deleting platform account with id: {}", id);
        platformAccountService.deleteAccount(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    /**
     * Borrow a platform account
     * POST /api/platform/accounts/{id}/borrow
     */
    @PostMapping("/{id}/borrow")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<PlatformAccountDTO> borrowAccount(
        @PathVariable Long id,
        @Valid @RequestBody BorrowAccountRequest request,
        @AuthenticationPrincipal User currentUser) {

        log.info("Borrowing platform account with id: {} by user: {}", id, currentUser.getUsername());
        PlatformAccountDTO updated = platformAccountService.borrowAccount(id, request, currentUser);
        return ResponseEntity.ok(updated);
    }

    /**
     * Return a borrowed platform account
     * POST /api/platform/accounts/{id}/return
     */
    @PostMapping("/{id}/return")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<PlatformAccountDTO> returnAccount(
        @PathVariable Long id,
        @AuthenticationPrincipal User currentUser) {

        log.info("Returning platform account with id: {} by user: {}", id, currentUser.getUsername());
        PlatformAccountDTO updated = platformAccountService.returnAccount(id, currentUser);
        return ResponseEntity.ok(updated);
    }

    /**
     * Get decrypted password for a platform account (ADMIN ONLY)
     * This action is audit logged
     * GET /api/platform/accounts/{id}/password
     */
    @GetMapping("/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PasswordResponse> getPassword(
        @PathVariable Long id,
        @AuthenticationPrincipal User currentUser) {

        log.warn("User {} is viewing password for account id: {}", currentUser.getUsername(), id);
        String password = platformAccountService.getPassword(id, currentUser);

        return ResponseEntity.ok(new PasswordResponse(password));
    }

    /**
     * Get platform account statistics
     * GET /api/platform/accounts/statistics
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<PlatformAccountStatisticsDTO> getStatistics() {
        log.debug("Fetching platform account statistics");
        PlatformAccountStatisticsDTO stats = platformAccountService.getStatistics();
        return ResponseEntity.ok(stats);
    }

    /**
     * Find overdue accounts
     * GET /api/platform/accounts/overdue
     */
    @GetMapping("/overdue")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<PlatformAccountDTO>> findOverdueAccounts() {
        log.debug("Fetching overdue platform accounts");
        List<PlatformAccountDTO> overdue = platformAccountService.findOverdueAccounts();
        return ResponseEntity.ok(overdue);
    }

    /**
     * Password response wrapper
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class PasswordResponse {
        private String password;
    }
}
