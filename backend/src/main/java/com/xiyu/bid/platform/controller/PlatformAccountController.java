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
import com.xiyu.bid.platform.infrastructure.persistence.entity.PlatformAccountImportTaskEntity;
import com.xiyu.bid.platform.service.PlatformAccountImportAppService;
import com.xiyu.bid.platform.service.PlatformAccountService;
import com.xiyu.bid.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** REST Controller for Platform Account Management. */
@RestController
@RequestMapping("/api/platform/accounts")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAuthority('resource')")
public class PlatformAccountController {

    private final PlatformAccountService platformAccountService;
    private final PlatformAccountImportAppService importAppService;
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
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<PlatformAccountDTO>> borrowAccount(
            @PathVariable Long id,
            @Valid @RequestBody BorrowAccountRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {
        log.info("Borrowing platform account with id: {}", id);
        PlatformAccountDTO updated = platformAccountService.borrowAccount(id, request, resolveUser(currentUser));
        return ResponseEntity.ok(ApiResponse.success("账号借用成功", updated));
    }

    @PostMapping("/{id}/return")
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
     * CO-400 round5 review: 不再覆盖类级 {@code @PreAuthorize("hasAuthority('resource')")}，
     * 让类级权限边界生效（所有平台账户接口要求 resource 权限），真权限
     * （管理员 OR 账户绑定联系人 custodianId）交给 Service 层 Policy。
     * 投标专员作为绑定联系人时可查看密码，非绑定联系人由 Service 抛 403。
     */
    @GetMapping("/{id}/password")
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
    public ResponseEntity<ApiResponse<PlatformAccountDTO>> returnAccountWithPassword(
            @PathVariable Long id,
            @Valid @RequestBody ReturnAccountRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {
        log.info("Returning platform account with id: {} and password change", id);
        PlatformAccountDTO updated = platformAccountService.returnAccount(id, request, resolveUser(currentUser));
        return ResponseEntity.ok(ApiResponse.success("账号归还成功（密码已更新）", updated));
    }

    // ── 批量导入 ────────────────────────────────────────────────────────────────

    /** 下载批量导入模板 */
    @GetMapping("/template")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        byte[] template = importAppService.generateTemplate();
        String filename = URLEncoder.encode("平台账户导入模板.xlsx", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .body(template);
    }

    /** 触发批量导入，返回 taskId */
    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<?>> importAccounts(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails currentUser) throws IOException {
        User user = resolveUser(currentUser);
        Long taskId = importAppService.triggerImport(
                file.getBytes(), file.getOriginalFilename(), user.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("导入任务已创建", java.util.Map.of("taskId", taskId)));
    }

    /** 查询导入任务状态 */
    @GetMapping("/import/tasks/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<PlatformAccountImportTaskEntity>> getImportTask(
            @PathVariable Long taskId) {
        PlatformAccountImportTaskEntity task = importAppService.getTask(taskId);
        if (task == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(ApiResponse.success(task));
    }

    /** 查询导入任务历史 */
    @GetMapping("/import/tasks")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<PlatformAccountImportTaskEntity>>> listImportTasks(
            @AuthenticationPrincipal UserDetails currentUser) {
        User user = resolveUser(currentUser);
        List<PlatformAccountImportTaskEntity> tasks = importAppService.listTasks(user.getId());
        return ResponseEntity.ok(ApiResponse.success(tasks));
    }

    /** Resolve User entity from UserDetails principal. */
    private User resolveUser(UserDetails userDetails) {
        return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new IllegalStateException("Current user not found: " + userDetails.getUsername()));
    }
}
