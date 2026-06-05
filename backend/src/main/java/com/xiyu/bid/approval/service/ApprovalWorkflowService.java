// Input: approval repositories, DTOs, and support services
// Output: Approval Workflow business service operations
// Pos: Service/业务层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.approval.service;

import com.xiyu.bid.approval.dto.ApprovalDetailDTO;
import com.xiyu.bid.approval.dto.ApprovalStatisticsDTO;
import com.xiyu.bid.approval.dto.ApprovalSubmitRequest;
import com.xiyu.bid.approval.enums.ApprovalStatus;
import com.xiyu.bid.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 审批流程服务
 * 实现审批的状态机逻辑和操作记录
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalWorkflowService {

    private final ApprovalCommandService approvalCommandService;
    private final ApprovalQueryService approvalQueryService;

    @Transactional
    public ApprovalDetailDTO submitForApproval(ApprovalSubmitRequest request, Long userId, String userName) {
        return approvalCommandService.submitForApproval(request, userId, userName);
    }

    @Transactional
    public ApprovalDetailDTO approve(UUID requestId, Long approverId, String approverName, String comment) {
        return approvalCommandService.approve(requestId, approverId, approverName, comment);
    }

    @Transactional
    public ApprovalDetailDTO reject(UUID requestId, Long approverId, String approverName, String reason) {
        return approvalCommandService.reject(requestId, approverId, approverName, reason);
    }

    @Transactional
    public void cancel(UUID requestId, Long userId, String userName) {
        approvalCommandService.cancel(requestId, userId, userName);
    }

    public Page<ApprovalDetailDTO> getPendingApprovals(
            Long currentUserId,
            User.Role currentUserRole,
            Long approverId,
            Pageable pageable
    ) {
        return approvalQueryService.getPendingApprovals(currentUserId, currentUserRole, approverId, pageable);
    }

    public ApprovalStatisticsDTO getStatistics() {
        return approvalQueryService.getStatistics();
    }

    public ApprovalDetailDTO getApprovalDetail(UUID requestId, Long currentUserId, User.Role currentUserRole) {
        return approvalQueryService.getApprovalDetail(requestId, currentUserId, currentUserRole);
    }

    @Transactional
    public void markAsRead(UUID requestId, Long userId) {
        approvalCommandService.markAsRead(requestId, userId);
    }

    @Transactional
    public Map<UUID, String> batchApprove(List<UUID> requestIds, Long approverId, String approverName, String comment) {
        return approvalCommandService.batchApprove(requestIds, approverId, approverName, comment);
    }

    @Transactional
    public Map<UUID, String> batchReject(List<UUID> requestIds, Long approverId, String approverName, String reason) {
        return approvalCommandService.batchReject(requestIds, approverId, approverName, reason);
    }

    public Page<ApprovalDetailDTO> getMyApprovals(Long userId, ApprovalStatus status, Pageable pageable) {
        return approvalQueryService.getMyApprovals(userId, status, pageable);
    }

    @Transactional
    public ApprovalDetailDTO resubmit(UUID requestId, Long userId, String userName, String newDescription) {
        return approvalCommandService.resubmit(requestId, userId, userName, newDescription);
    }
}
