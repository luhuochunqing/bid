// Input: PlatformAccountBorrowService, DTOs
// Output: REST API endpoints for account borrow application workflow
// Pos: Controller/控制器层 — 账号借用申请全生命周期

package com.xiyu.bid.platform.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.platform.dto.PlatformAccountBorrowApprovalRequest;
import com.xiyu.bid.platform.dto.BorrowApplicationDTO;
import com.xiyu.bid.platform.dto.BorrowApplicationRequest;
import com.xiyu.bid.platform.dto.PlatformAccountBorrowRejectionRequest;
import com.xiyu.bid.platform.dto.ReturnBorrowApplicationRequest;
import com.xiyu.bid.platform.notification.PlatformAccountBorrowNotificationService;
import com.xiyu.bid.platform.service.PlatformAccountBorrowService;
import com.xiyu.bid.platform.service.PlatformAccountViewerPolicy;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.security.EffectiveRoleResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

import java.time.LocalDateTime;
import java.util.List;

/** REST Controller for platform account borrow application workflow. */
@RestController
@RequestMapping("/api")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
@Slf4j
public class PlatformAccountBorrowController {

    private final PlatformAccountBorrowService borrowService;
    private final UserRepository userRepository;
    private final PlatformAccountBorrowNotificationService notificationService;
    /** CO-373/CO-403: 统一角色码解析入口。 */
    private final EffectiveRoleResolver effectiveRoleResolver;

    /** Submit a new borrow application for a platform account. */
    @PostMapping("/platform/accounts/{accountId}/borrow-applications")
    public ResponseEntity<ApiResponse<BorrowApplicationDTO>> submitApplication(
            @PathVariable Long accountId,
            @Valid @RequestBody BorrowApplicationRequest request,
            Principal principal) {
        User user = resolveUser(principal);
        request.setAccountId(accountId);
        BorrowApplicationDTO result = borrowService.submitApplication(request, user);
        notificationService.notifySubmitted(result);
        return ResponseEntity.ok(ApiResponse.success("申请已提交", result));
    }

    /** List borrow applications submitted by the current user. */
    @GetMapping("/borrow-applications/my-applications")
    public ResponseEntity<ApiResponse<List<BorrowApplicationDTO>>> myApplications(
            Principal principal) {
        User user = resolveUser(principal);
        List<BorrowApplicationDTO> result = borrowService.getApplications(user.getId(), null, null);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * List borrow applications waiting for the current user's approval.
     * CO-403: 管理员角色可查看所有待审批申请，普通用户只看自己为绑定联系人的申请。
     */
    @GetMapping("/borrow-applications/my-approvals")
    public ResponseEntity<ApiResponse<List<BorrowApplicationDTO>>> myApprovals(
            Principal principal) {
        User user = resolveUser(principal);
        boolean privileged = isPrivileged(user);
        if (privileged) {
            List<BorrowApplicationDTO> result = borrowService.findAllApprovals(null);
            return ResponseEntity.ok(ApiResponse.success(result));
        }
        List<BorrowApplicationDTO> result = borrowService.findAllApprovals(user.getId());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /** Approve a pending borrow application. */
    @PostMapping("/borrow-applications/{id}/approve")
    public ResponseEntity<ApiResponse<BorrowApplicationDTO>> approveApplication(
            @PathVariable Long id,
            @Valid @RequestBody PlatformAccountBorrowApprovalRequest request,
            Principal principal) {
        User user = resolveUser(principal);
        boolean privileged = isPrivileged(user);
        BorrowApplicationDTO result = borrowService.approveApplication(id, request.getComment(), user, privileged);
        notificationService.notifyApproved(result);
        return ResponseEntity.ok(ApiResponse.success("申请已通过", result));
    }

    /** Reject a pending borrow application. */
    @PostMapping("/borrow-applications/{id}/reject")
    public ResponseEntity<ApiResponse<BorrowApplicationDTO>> rejectApplication(
            @PathVariable Long id,
            @Valid @RequestBody PlatformAccountBorrowRejectionRequest request,
            Principal principal) {
        User user = resolveUser(principal);
        boolean privileged = isPrivileged(user);
        BorrowApplicationDTO result = borrowService.rejectApplication(id, request.getComment(), user, privileged);
        notificationService.notifyRejected(result, request.getComment());
        return ResponseEntity.ok(ApiResponse.success("申请已拒绝", result));
    }

    /** Cancel a pending borrow application (by applicant). */
    @PostMapping("/borrow-applications/{id}/cancel")
    public ResponseEntity<ApiResponse<BorrowApplicationDTO>> cancelApplication(
            @PathVariable Long id,
            Principal principal) {
        User user = resolveUser(principal);
        BorrowApplicationDTO result = borrowService.cancelApplication(id, user);
        notificationService.notifyCancelled(result);
        return ResponseEntity.ok(ApiResponse.success("申请已撤销", result));
    }

    /** Return a borrowed account with mandatory password change. */
    @PostMapping("/borrow-applications/{id}/return")
    public ResponseEntity<ApiResponse<BorrowApplicationDTO>> returnAccount(
            @PathVariable Long id,
            @Valid @RequestBody ReturnBorrowApplicationRequest request,
            Principal principal) {
        User user = resolveUser(principal);
        boolean privileged = isPrivileged(user);
        LocalDateTime actualReturnedAt = parseReturnedAt(request.getActualReturnedAt());
        BorrowApplicationDTO result = borrowService.returnAccount(
                id, request.getNewPassword(), actualReturnedAt, user, privileged);
        notificationService.notifyReturned(result);
        return ResponseEntity.ok(ApiResponse.success("账号已归还，密码已更新", result));
    }

    /** Resolve User entity from Principal. */
    private User resolveUser(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new BusinessException("当前用户未登录");
        }
        return userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new BusinessException("当前用户不存在: " + principal.getName()));
    }

    /** CO-403: 判断当前用户是否为管理员角色（admin / bidAdmin / bid-TeamLeader）。 */
    private boolean isPrivileged(User user) {
        String roleCode = effectiveRoleResolver.resolveRoleCode(user);
        return PlatformAccountViewerPolicy.isPrivilegedRole(roleCode);
    }

    private LocalDateTime parseReturnedAt(String value) {
        try {
            return LocalDateTime.parse(value);
        } catch (java.time.format.DateTimeParseException e) {
            throw new BusinessException("实际归还时间格式不正确: " + value);
        }
    }
}
