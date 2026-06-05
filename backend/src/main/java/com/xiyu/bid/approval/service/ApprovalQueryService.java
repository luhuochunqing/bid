package com.xiyu.bid.approval.service;

import com.xiyu.bid.approval.dto.ApprovalActionDTO;
import com.xiyu.bid.approval.dto.ApprovalDetailDTO;
import com.xiyu.bid.approval.dto.ApprovalStatisticsDTO;
import com.xiyu.bid.approval.entity.ApprovalAction;
import com.xiyu.bid.approval.entity.ApprovalRequest;
import com.xiyu.bid.approval.enums.ApprovalStatus;
import com.xiyu.bid.approval.repository.ApprovalActionRepository;
import com.xiyu.bid.approval.repository.ApprovalRequestRepository;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class ApprovalQueryService {

    private final ApprovalRequestRepository requestRepository;
    private final ApprovalActionRepository actionRepository;

    Page<ApprovalDetailDTO> getPendingApprovals(Long currentUserId, User.Role currentUserRole, Long approverId, Pageable pageable) {
        enforce(ApprovalPermissionPolicy.canReadPendingQueue(currentUserId, currentUserRole, approverId));

        List<ApprovalRequest> requests;
        if (approverId != null) {
            requests = requestRepository.findByStatusAndCurrentApproverIdOrderByPriorityDescCreatedAtDesc(
                    ApprovalStatus.PENDING, approverId);
        } else if (ApprovalPermissionPolicy.isPrivileged(currentUserRole)) {
            requests = requestRepository.findByStatusOrderByPriorityDescCreatedAtDesc(ApprovalStatus.PENDING);
        } else {
            requests = requestRepository.findByStatusAndCurrentApproverIdOrderByPriorityDescCreatedAtDesc(
                    ApprovalStatus.PENDING, currentUserId);
        }

        return paginate(requests.stream().map(this::toDetailDTO).toList(), pageable);
    }

    ApprovalStatisticsDTO getStatistics() {
        Long totalCount = requestRepository.count();
        Map<String, Long> statusCounts = new HashMap<>();
        for (Object[] row : requestRepository.countByStatus()) {
            statusCounts.put(String.valueOf(row[0]), (Long) row[1]);
        }

        Long pendingCount = statusCounts.getOrDefault(ApprovalStatus.PENDING.name(), 0L);
        Long approvedCount = statusCounts.getOrDefault(ApprovalStatus.APPROVED.name(), 0L);
        Long rejectedCount = statusCounts.getOrDefault(ApprovalStatus.REJECTED.name(), 0L);
        Long cancelledCount = statusCounts.getOrDefault(ApprovalStatus.CANCELLED.name(), 0L);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);
        LocalDateTime monthStart = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
        LocalDateTime nextMonthStart = monthStart.plusMonths(1);

        Long todaySubmitted = requestRepository.countBySubmittedAtBetween(todayStart, tomorrowStart);
        Long monthSubmitted = requestRepository.countBySubmittedAtBetweenAndStatusIn(
                monthStart,
                nextMonthStart,
                List.of(ApprovalStatus.PENDING, ApprovalStatus.APPROVED, ApprovalStatus.REJECTED, ApprovalStatus.CANCELLED)
        );
        Long overdueCount = requestRepository.countByStatusAndDueDateBefore(ApprovalStatus.PENDING, now);
        Long nearDueCount = requestRepository.countByStatusAndDueDateBetween(ApprovalStatus.PENDING, now, now.plusHours(24));

        Double avgProcessingHours = requestRepository.findByStatusInAndCompletedAtIsNotNull(
                        List.of(ApprovalStatus.APPROVED, ApprovalStatus.REJECTED)
                ).stream()
                .filter(item -> item.getSubmittedAt() != null && item.getCompletedAt() != null)
                .mapToLong(item -> ChronoUnit.MINUTES.between(item.getSubmittedAt(), item.getCompletedAt()))
                .average()
                .stream()
                .map(avgMinutes -> avgMinutes / 60.0d)
                .boxed()
                .findFirst()
                .orElse(null);

        Double approvalRate = null;
        long totalDecisions = approvedCount + rejectedCount;
        if (totalDecisions > 0) {
            approvalRate = (approvedCount.doubleValue() / totalDecisions) * 100;
        }

        Map<String, Long> byType = new HashMap<>();
        for (Object[] row : requestRepository.countByType()) {
            byType.put((String) row[0], (Long) row[1]);
        }

        Map<Integer, Long> byPriority = new HashMap<>();
        for (Object[] row : requestRepository.countByPriority()) {
            byPriority.put((Integer) row[0], (Long) row[1]);
        }

        return ApprovalStatisticsDTO.builder()
                .totalCount(totalCount)
                .pendingCount(pendingCount)
                .approvedCount(approvedCount)
                .rejectedCount(rejectedCount)
                .cancelledCount(cancelledCount)
                .todaySubmitted(todaySubmitted)
                .monthSubmitted(monthSubmitted)
                .overdueCount(overdueCount)
                .nearDueCount(nearDueCount)
                .avgProcessingHours(avgProcessingHours)
                .approvalRate(approvalRate)
                .byType(byType)
                .byPriority(byPriority)
                .build();
    }

    ApprovalDetailDTO getApprovalDetail(UUID requestId, Long currentUserId, User.Role currentUserRole) {
        ApprovalRequest request = getApprovalRequestEntity(requestId);
        enforce(ApprovalPermissionPolicy.canView(request, currentUserId, currentUserRole));
        return toDetailDTO(request);
    }

    Page<ApprovalDetailDTO> getMyApprovals(Long userId, ApprovalStatus status, Pageable pageable) {
        List<ApprovalDetailDTO> dtos = requestRepository.findByRequesterIdOrderByCreatedAtDesc(userId).stream()
                .filter(request -> status == null || request.getStatus() == status)
                .map(this::toDetailDTO)
                .toList();
        return paginate(dtos, pageable);
    }

    ApprovalRequest getApprovalRequestEntity(UUID requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("审批请求不存在: " + requestId));
    }

    ApprovalDetailDTO toDetailDTO(ApprovalRequest request) {
        List<ApprovalActionDTO> actionDTOs = actionRepository.findByApprovalRequestIdOrderByActionTimeAsc(request.getId()).stream()
                .map(this::toActionDTO)
                .toList();

        Long processingHours = null;
        if (request.getCompletedAt() != null && request.getSubmittedAt() != null) {
            processingHours = ChronoUnit.HOURS.between(request.getSubmittedAt(), request.getCompletedAt());
        }

        return ApprovalDetailDTO.builder()
                .id(request.getId())
                .projectId(request.getProjectId())
                .projectName(request.getProjectName())
                .approvalType(request.getApprovalType())
                .status(request.getStatus())
                .statusDescription(request.getStatus().getDescription())
                .requesterId(request.getRequesterId())
                .requesterName(request.getRequesterName())
                .currentApproverId(request.getCurrentApproverId())
                .currentApproverName(request.getCurrentApproverName())
                .priority(request.getPriority())
                .title(request.getTitle())
                .description(request.getDescription())
                .submittedAt(request.getSubmittedAt())
                .completedAt(request.getCompletedAt())
                .dueDate(request.getDueDate())
                .isRead(request.getIsRead())
                .isOverdue(request.isOverdue())
                .isNearDueDate(request.isNearDueDate())
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .actions(actionDTOs)
                .processingHours(processingHours)
                .build();
    }

    private ApprovalActionDTO toActionDTO(ApprovalAction action) {
        return ApprovalActionDTO.builder()
                .id(action.getId())
                .actionType(action.getActionType())
                .actorId(action.getActorId())
                .actorName(action.getActorName())
                .comment(action.getComment())
                .actionTime(action.getActionTime())
                .previousStatus(action.getPreviousStatus() != null ? action.getPreviousStatus().name() : null)
                .newStatus(action.getNewStatus() != null ? action.getNewStatus().name() : null)
                .build();
    }

    private Page<ApprovalDetailDTO> paginate(List<ApprovalDetailDTO> dtos, Pageable pageable) {
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), dtos.size());
        List<ApprovalDetailDTO> pageContent = start < dtos.size() ? dtos.subList(start, end) : List.of();
        return new PageImpl<>(pageContent, pageable, dtos.size());
    }

    private void enforce(ApprovalDecisionPolicy.Decision decision) {
        if (!decision.permitted()) {
            throw new BusinessException(decision.message());
        }
    }
}
