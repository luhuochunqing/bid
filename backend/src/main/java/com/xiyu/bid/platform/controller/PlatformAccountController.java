// Input: PlatformAccountService, DTOs
// Output: REST API Endpoints with uniform ApiResponse
// Pos: Controller/控制器层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.platform.controller;

import com.xiyu.bid.dto.ApiResponse;
import java.util.List;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.platform.dto.BorrowAccountRequest;
import com.xiyu.bid.platform.dto.PlatformAccountCreateRequest;
import com.xiyu.bid.platform.dto.PlatformAccountDTO;
import com.xiyu.bid.platform.dto.PlatformAccountStatisticsDTO;
import com.xiyu.bid.platform.service.PlatformAccountService;
import jakarta.validation.Valid;
import java.util.List;
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

/** REST Controller for Platform Account Management. */
@RestController
@RequestMapping("/api/platform/accounts")
@RequiredArgsConstructor
@Slf4j
public class PlatformAccountController {

    private final PlatformAccountService platformAccountService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<PlatformAccountDTO>> createAccount(
            @Valid @RequestBody PlatformAccountCreateRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Creating platform account: {}", request.getAccountName());
        PlatformAccountDTO created = platformAccountService.createAccount(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("账号创建成功", created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PlatformAccountDTO>>> getAllAccounts() {
        log.debug("Fetching all platform accounts");
        List<PlatformAccountDTO> accounts = platformAccountService.getAllAccounts();
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
            @AuthenticationPrincipal User currentUser) {
        log.info("Updating platform account with id: {}", id);
        PlatformAccountDTO updated = platformAccountService.updateAccount(id, request, currentUser);
        return ResponseEntity.ok(ApiResponse.success("账号更新成功", updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        log.info("Deleting platform account with id: {}", id);
        platformAccountService.deleteAccount(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success("账号删除成功", null));
    }

    @PostMapping("/{id}/borrow")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<PlatformAccountDTO>> borrowAccount(
            @PathVariable Long id,
            @Valid @RequestBody BorrowAccountRequest request,
            @AuthenticationPrincipal User currentUser) {
        log.info("Borrowing platform account with id: {} by user: {}", id, currentUser.getUsername());
        PlatformAccountDTO updated = platformAccountService.borrowAccount(id, request, currentUser);
        return ResponseEntity.ok(ApiResponse.success("账号借用成功", updated));
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<PlatformAccountDTO>> returnAccount(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        log.info("Returning platform account with id: {} by user: {}", id, currentUser.getUsername());
        PlatformAccountDTO updated = platformAccountService.returnAccount(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success("账号归还成功", updated));
    }

    @GetMapping("/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> getPassword(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        log.warn("User {} is viewing password for account id: {}", currentUser.getUsername(), id);
        String password = platformAccountService.getPassword(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success(password));
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
}
