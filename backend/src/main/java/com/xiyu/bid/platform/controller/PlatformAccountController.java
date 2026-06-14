// Input: PlatformAccountService, DTOs
// Output: REST API Endpoints with uniform ApiResponse
// Pos: Controller/控制器层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.platform.controller;

import com.xiyu.bid.dto.ApiResponse;
import java.util.List;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.platform.dto.BorrowAccountRequest;
import com.xiyu.bid.platform.dto.PasswordRevealResponse;
import com.xiyu.bid.platform.dto.PlatformAccountCreateRequest;
import com.xiyu.bid.platform.dto.PlatformAccountDTO;
import com.xiyu.bid.platform.dto.PlatformAccountStatisticsDTO;
import com.xiyu.bid.platform.dto.ReturnAccountRequest;
import com.xiyu.bid.platform.service.PlatformAccountService;
import com.xiyu.bid.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/** REST Controller for Platform Account Management. */
@RestController
@RequestMapping("/api/platform/accounts")
@RequiredArgsConstructor
@Slf4j
public class PlatformAccountController {

    private final PlatformAccountService platformAccountService;
    private final UserRepository userRepository;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<PlatformAccountDTO>> createAccount(
            @Valid @RequestBody PlatformAccountCreateRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {
        log.info("Creating platform account: {}", request.getAccountName());
        PlatformAccountDTO created = platformAccountService.createAccount(request, resolveUser(currentUser));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("账号创建成功", created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<?>>> getAllAccounts(
            @AuthenticationPrincipal User currentUser) {
        log.debug("Fetching all platform accounts for user: {}",
            currentUser != null ? currentUser.getUsername() : "anonymous");
        List<?> accounts = platformAccountService.getAccountsForViewer(currentUser);
        return ResponseEntity.ok(ApiResponse.success(accounts));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PlatformAccountDTO>> getAccountById(@PathVariable Long id) {
        log.debug("Fetching platform account with id: {}", id);
        PlatformAccountDTO account = platformAccountService.getAccountById(id);
        return ResponseEntity.ok(ApiResponse.success(account));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<PlatformAccountDTO>> updateAccount(
            @PathVariable Long id,
            @Valid @RequestBody PlatformAccountCreateRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {
        log.info("Updating platform account with id: {}", id);
        PlatformAccountDTO updated = platformAccountService.updateAccount(id, request, resolveUser(currentUser));
        return ResponseEntity.ok(ApiResponse.success("账号更新成功", updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails currentUser) {
        log.info("Deleting platform account with id: {}", id);
        platformAccountService.deleteAccount(id, resolveUser(currentUser));
        return ResponseEntity.ok(ApiResponse.success("账号删除成功", null));
    }

    @PostMapping("/{id}/borrow")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<PlatformAccountDTO>> borrowAccount(
            @PathVariable Long id,
            @Valid @RequestBody BorrowAccountRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {
        log.info("Borrowing platform account with id: {}", id);
        PlatformAccountDTO updated = platformAccountService.borrowAccount(id, request, resolveUser(currentUser));
        return ResponseEntity.ok(ApiResponse.success("账号借用成功", updated));
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<PlatformAccountDTO>> returnAccount(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails currentUser) {
        log.info("Returning platform account with id: {}", id);
        PlatformAccountDTO updated = platformAccountService.returnAccount(id, resolveUser(currentUser));
        return ResponseEntity.ok(ApiResponse.success("账号归还成功", updated));
    }

    /**
     * Reveal the plaintext password for a platform account.
     *
     * <p>Security (H12): the response is wrapped in
     * {@link PasswordRevealResponse} with an explicit {@code expiresAt}
     * window and a fresh {@code auditId} so:
     * <ul>
     *   <li>the client can show a "visible for 5 minutes" indicator;</li>
     *   <li>the server-side audit log can correlate a later access event
     *   to this reveal by id;</li>
     *   <li>the response carries {@code Cache-Control: no-store} so
     *   browsers / proxies do not persist the secret in shared caches.</li>
     * </ul>
     * The endpoint is restricted to {@code ADMIN} via {@code @PreAuthorize}.
     */
    @GetMapping("/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PasswordRevealResponse>> getPassword(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails currentUser) {
        String auditId = UUID.randomUUID().toString();
        log.warn("User {} is revealing password for account id: {} (auditId={})",
                currentUser.getUsername(), id, auditId);
        String password = platformAccountService.getPassword(id, resolveUser(currentUser));
        PasswordRevealResponse payload = PasswordRevealResponse.builder()
                .password(password)
                .expiresAt(LocalDateTime.now().plus(Duration.ofMinutes(5)))
                .auditId(auditId)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(payload));
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<PlatformAccountStatisticsDTO>> getStatistics() {
        log.debug("Fetching platform account statistics");
        PlatformAccountStatisticsDTO stats = platformAccountService.getStatistics();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/overdue")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<PlatformAccountDTO>>> findOverdueAccounts() {
        log.debug("Fetching overdue platform accounts");
        List<PlatformAccountDTO> overdue = platformAccountService.findOverdueAccounts();
        return ResponseEntity.ok(ApiResponse.success(overdue));
    }

    @PostMapping("/{id}/return-with-password")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<PlatformAccountDTO>> returnAccountWithPassword(
            @PathVariable Long id,
            @Valid @RequestBody ReturnAccountRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {
        log.info("Returning platform account with id: {} and password change", id);
        PlatformAccountDTO updated = platformAccountService.returnAccount(id, request, resolveUser(currentUser));
        return ResponseEntity.ok(ApiResponse.success("账号归还成功（密码已更新）", updated));
    }

    /** Resolve User entity from UserDetails principal. */
    private User resolveUser(UserDetails userDetails) {
        return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new IllegalStateException("Current user not found: " + userDetails.getUsername()));
    }
}
