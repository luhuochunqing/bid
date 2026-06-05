// Input: approval service and request DTOs
// Output: Approval REST API endpoints
// Pos: Controller/控制器层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.approval.controller;

import com.xiyu.bid.approval.dto.ApprovalDecisionRequest;
import com.xiyu.bid.approval.dto.ApprovalDetailDTO;
import com.xiyu.bid.approval.dto.ApprovalStatisticsDTO;
import com.xiyu.bid.approval.dto.ApprovalSubmitRequest;
import com.xiyu.bid.approval.enums.ApprovalStatus;
import com.xiyu.bid.approval.service.ApprovalWorkflowService;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 审批流程Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalWorkflowService approvalWorkflowService;
    private final AuthService authService;

    /**
     * 提交审批
     */
    @PostMapping("/submit")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> submitApproval(
            @Valid @RequestBody ApprovalSubmitRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = getUserIdFromDetails(userDetails);
        String userName = getCurrentUser(userDetails).getUsername();

        ApprovalDetailDTO result = approvalWorkflowService.submitForApproval(request, userId, userName);

        Map<String, Object> data = new HashMap<>();
        data.put("id", result.getId());
        data.put("status", result.getStatus());
        data.put("message", "审批提交成功");

        return ApiResponse.success(data);
    }

    /**
     * 审批通过
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> approve(
            @PathVariable UUID id,
            @Valid @RequestBody ApprovalDecisionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = getUserIdFromDetails(userDetails);
        String userName = getCurrentUser(userDetails).getUsername();

        ApprovalDetailDTO result = approvalWorkflowService.approve(id, userId, userName, request.getComment());

        Map<String, Object> data = new HashMap<>();
        data.put("id", result.getId());
        data.put("status", result.getStatus());
        data.put("message", "审批通过");

        return ApiResponse.success(data);
    }

    /**
     * 审批驳回
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> reject(
            @PathVariable UUID id,
            @Valid @RequestBody ApprovalDecisionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = getUserIdFromDetails(userDetails);
        String userName = getCurrentUser(userDetails).getUsername();

        ApprovalDetailDTO result = approvalWorkflowService.reject(id, userId, userName, request.getComment());

        Map<String, Object> data = new HashMap<>();
        data.put("id", result.getId());
        data.put("status", result.getStatus());
        data.put("requireResubmit", request.getRequireResubmit());
        data.put("message", "审批已驳回");

        return ApiResponse.success(data);
    }

    /**
     * 取消审批
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> cancel(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = getUserIdFromDetails(userDetails);
        String userName = getCurrentUser(userDetails).getUsername();

        approvalWorkflowService.cancel(id, userId, userName);

        return ApiResponse.success("审批已取消", null);
    }

    /**
     * 获取待审批列表
     */
    @GetMapping("/pending")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<ApprovalDetailDTO>> getPendingApprovals(
            @RequestParam(required = false) Long approverId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = getCurrentUser(userDetails);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "submittedAt"));
        Page<ApprovalDetailDTO> result = approvalWorkflowService.getPendingApprovals(
                currentUser.getId(),
                currentUser.getRole(),
                approverId,
                pageable
        );

        return ApiResponse.success(result);
    }

    /**
     * 获取统计数据
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<ApprovalStatisticsDTO> getStatistics() {
        ApprovalStatisticsDTO stats = approvalWorkflowService.getStatistics();
        return ApiResponse.success(stats);
    }

    /**
     * 获取审批详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ApprovalDetailDTO> getApprovalDetail(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        ApprovalDetailDTO detail = approvalWorkflowService.getApprovalDetail(id, currentUser.getId(), currentUser.getRole());
        return ApiResponse.success(detail);
    }

    /**
     * 标记为已读
     */
    @PutMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<String> markAsRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = getUserIdFromDetails(userDetails);
        approvalWorkflowService.markAsRead(id, userId);

        return ApiResponse.success("已标记为已读", "已标记为已读");
    }

    /**
     * 获取我的审批列表 (我提交的)
     */
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<ApprovalDetailDTO>> getMyApprovals(
            @RequestParam(required = false) ApprovalStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = getUserIdFromDetails(userDetails);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "submittedAt"));
        Page<ApprovalDetailDTO> result = approvalWorkflowService.getMyApprovals(userId, status, pageable);

        return ApiResponse.success(result);
    }

    /**
     * 批量审批通过
     */
    @PostMapping("/batch/approve")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<UUID, String>> batchApprove(
            @RequestBody Map<String, Object> requestBody,
            @AuthenticationPrincipal UserDetails userDetails) {

        @SuppressWarnings("unchecked")
        List<UUID> ids = (List<UUID>) requestBody.get("ids");
        String comment = (String) requestBody.getOrDefault("comment", "批量通过");

        Long userId = getUserIdFromDetails(userDetails);
        String userName = getCurrentUser(userDetails).getUsername();

        Map<UUID, String> results = approvalWorkflowService.batchApprove(ids, userId, userName, comment);

        return ApiResponse.success(results);
    }

    /**
     * 批量驳回
     */
    @PostMapping("/batch/reject")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<UUID, String>> batchReject(
            @RequestBody Map<String, Object> requestBody,
            @AuthenticationPrincipal UserDetails userDetails) {

        @SuppressWarnings("unchecked")
        List<UUID> ids = (List<UUID>) requestBody.get("ids");
        String comment = (String) requestBody.getOrDefault("comment", "批量驳回");

        Long userId = getUserIdFromDetails(userDetails);
        String userName = getCurrentUser(userDetails).getUsername();

        Map<UUID, String> results = approvalWorkflowService.batchReject(ids, userId, userName, comment);

        return ApiResponse.success(results);
    }

    /**
     * 重新提交审批
     */
    @PostMapping("/{id}/resubmit")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ApprovalDetailDTO> resubmit(
            @PathVariable UUID id,
            @RequestBody Map<String, String> requestBody,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = getUserIdFromDetails(userDetails);
        String userName = getCurrentUser(userDetails).getUsername();
        String newDescription = requestBody.get("description");

        ApprovalDetailDTO result = approvalWorkflowService.resubmit(id, userId, userName, newDescription);

        return ApiResponse.success(result);
    }

    /**
     * 从UserDetails获取用户ID
     *
     * SECURITY: 认证主体中的 username 是登录名，不是数值 userId。
     * 这里显式查库解析当前用户，避免控制器把 username 错当成 userId。
     */
    private Long getUserIdFromDetails(UserDetails userDetails) {
        return getCurrentUser(userDetails).getId();
    }

    private User getCurrentUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new org.springframework.security.authentication.AuthenticationServiceException(
                    "UserDetails cannot be null");
        }

        String username = userDetails.getUsername();
        if (username == null || username.trim().isEmpty()) {
            throw new org.springframework.security.authentication.AuthenticationServiceException(
                    "Username cannot be null or empty");
        }

        try {
            return authService.resolveUserByUsername(username.trim());
        } catch (org.springframework.security.core.userdetails.UsernameNotFoundException ex) {
            throw new org.springframework.security.authentication.AuthenticationServiceException(
                    "Authenticated user not found: " + username, ex);
        }
    }
}
