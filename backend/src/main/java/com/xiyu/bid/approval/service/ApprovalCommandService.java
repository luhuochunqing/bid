package com.xiyu.bid.approval.service;

import com.xiyu.bid.approval.dto.ApprovalDetailDTO;
import com.xiyu.bid.approval.dto.ApprovalSubmitRequest;
import com.xiyu.bid.approval.entity.ApprovalRequest;
import com.xiyu.bid.approval.enums.ApprovalActionType;
import com.xiyu.bid.approval.enums.ApprovalStatus;
import com.xiyu.bid.approval.repository.ApprovalRequestRepository;
import com.xiyu.bid.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
class ApprovalCommandService {

    private static final Long DEFAULT_APPROVER_ID = 1L;

    private final ApprovalRequestRepository requestRepository;
    private final ApprovalActionRecorder actionRecorder;
    private final ApprovalQueryService approvalQueryService;

    @Transactional
    ApprovalDetailDTO submitForApproval(ApprovalSubmitRequest request, Long userId, String userName) {
        log.info("用户 {} 提交审批: 项目={}, 类型={}", userId, request.getProjectId(), request.getApprovalType());
        enforce(ApprovalDecisionPolicy.canSubmit(
                requestRepository.findByProjectIdOrderByCreatedAtDesc(request.getProjectId())
        ));

        ApprovalRequest approvalRequest = ApprovalRequest.builder()
                .projectId(request.getProjectId())
                .projectName(request.getProjectName())
                .approvalType(request.getApprovalType())
                .status(ApprovalStatus.PENDING)
                .requesterId(userId)
                .requesterName(userName)
                .currentApproverId(request.getApproverId() != null ? request.getApproverId() : DEFAULT_APPROVER_ID)
                .currentApproverName(null)
                .priority(request.getPriority() != null ? request.getPriority() : 0)
                .title(request.getTitle())
                .description(request.getDescription())
                .dueDate(request.getDueDate())
                .submittedAt(LocalDateTime.now())
                .isRead(false)
                .build();

        if (request.getAttachmentIds() != null && !request.getAttachmentIds().isEmpty()) {
            approvalRequest.setAttachmentIds(request.getAttachmentIds().stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(",")));
        }

        approvalRequest = requestRepository.save(approvalRequest);
        actionRecorder.record(approvalRequest.getId(), ApprovalActionType.SUBMIT, userId, userName,
                request.getDescription(), null, ApprovalStatus.PENDING);
        return approvalQueryService.toDetailDTO(approvalRequest);
    }

    @Transactional
    ApprovalDetailDTO approve(UUID requestId, Long approverId, String approverName, String comment) {
        log.info("用户 {} 审批通过: {}", approverId, requestId);
        ApprovalRequest request = approvalQueryService.getApprovalRequestEntity(requestId);
        enforce(ApprovalDecisionPolicy.canApprove(request));
        enforce(ApprovalPermissionPolicy.canApprove(request, approverId));
        ApprovalStatus previousStatus = request.getStatus();
        request.setStatus(ApprovalStatus.APPROVED);
        request.setCompletedAt(LocalDateTime.now());
        request.setIsRead(true);
        request = requestRepository.save(request);
        actionRecorder.record(request.getId(), ApprovalActionType.APPROVE, approverId, approverName,
                comment, previousStatus, ApprovalStatus.APPROVED);
        return approvalQueryService.toDetailDTO(request);
    }

    @Transactional
    ApprovalDetailDTO reject(UUID requestId, Long approverId, String approverName, String reason) {
        log.info("用户 {} 审批驳回: {}", approverId, requestId);
        ApprovalRequest request = approvalQueryService.getApprovalRequestEntity(requestId);
        enforce(ApprovalDecisionPolicy.canApprove(request));
        enforce(ApprovalPermissionPolicy.canApprove(request, approverId));
        ApprovalStatus previousStatus = request.getStatus();
        request.setStatus(ApprovalStatus.REJECTED);
        request.setCompletedAt(LocalDateTime.now());
        request.setIsRead(true);
        request = requestRepository.save(request);
        actionRecorder.record(request.getId(), ApprovalActionType.REJECT, approverId, approverName,
                reason, previousStatus, ApprovalStatus.REJECTED);
        return approvalQueryService.toDetailDTO(request);
    }

    @Transactional
    void cancel(UUID requestId, Long userId, String userName) {
        log.info("用户 {} 取消审批: {}", userId, requestId);
        ApprovalRequest request = approvalQueryService.getApprovalRequestEntity(requestId);
        enforce(ApprovalDecisionPolicy.canCancel(request, userId));
        ApprovalStatus previousStatus = request.getStatus();
        request.setStatus(ApprovalStatus.CANCELLED);
        request.setCompletedAt(LocalDateTime.now());
        requestRepository.save(request);
        actionRecorder.record(request.getId(), ApprovalActionType.CANCEL, userId, userName,
                "申请人取消审批", previousStatus, ApprovalStatus.CANCELLED);
    }

    @Transactional
    void markAsRead(UUID requestId, Long userId) {
        ApprovalRequest request = approvalQueryService.getApprovalRequestEntity(requestId);
        enforce(ApprovalPermissionPolicy.canMarkRead(request, userId));
        if (!request.getIsRead()) {
            request.setIsRead(true);
            requestRepository.save(request);
        }
    }

    @Transactional
    Map<UUID, String> batchApprove(List<UUID> requestIds, Long approverId, String approverName, String comment) {
        return batchExecute(requestIds, requestId -> approve(requestId, approverId, approverName, comment), "审批成功", "审批失败: ");
    }

    @Transactional
    Map<UUID, String> batchReject(List<UUID> requestIds, Long approverId, String approverName, String reason) {
        return batchExecute(requestIds, requestId -> reject(requestId, approverId, approverName, reason), "驳回成功", "驳回失败: ");
    }

    @Transactional
    ApprovalDetailDTO resubmit(UUID requestId, Long userId, String userName, String newDescription) {
        ApprovalRequest originalRequest = approvalQueryService.getApprovalRequestEntity(requestId);
        enforce(ApprovalDecisionPolicy.canResubmit(originalRequest, userId));
        ApprovalSubmitRequest submitRequest = ApprovalSubmitRequest.builder()
                .projectId(originalRequest.getProjectId())
                .projectName(originalRequest.getProjectName())
                .approvalType(originalRequest.getApprovalType())
                .title(originalRequest.getTitle())
                .description(newDescription != null ? newDescription : originalRequest.getDescription())
                .priority(originalRequest.getPriority())
                .dueDate(originalRequest.getDueDate())
                .approverId(originalRequest.getCurrentApproverId())
                .build();
        return submitForApproval(submitRequest, userId, userName);
    }

    private Map<UUID, String> batchExecute(
            List<UUID> requestIds,
            ApprovalHandler handler,
            String successMessage,
            String failurePrefix
    ) {
        Map<UUID, String> results = new HashMap<>();
        for (UUID requestId : requestIds) {
            try {
                handler.handle(requestId);
                results.put(requestId, successMessage);
            } catch (RuntimeException exception) {
                results.put(requestId, failurePrefix + exception.getMessage());
            }
        }
        return results;
    }

    private void enforce(ApprovalDecisionPolicy.Decision decision) {
        if (!decision.permitted()) {
            throw new BusinessException(decision.message());
        }
    }

    @FunctionalInterface
    private interface ApprovalHandler {
        void handle(UUID requestId);
    }
}
