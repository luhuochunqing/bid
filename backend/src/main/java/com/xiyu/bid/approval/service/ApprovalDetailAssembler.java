// Input: approval entities and action repository
// Output: approval detail/statistics DTOs
// Pos: Service/业务层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.approval.service;

import com.xiyu.bid.approval.dto.ApprovalActionDTO;
import com.xiyu.bid.approval.dto.ApprovalDetailDTO;
import com.xiyu.bid.approval.entity.ApprovalAction;
import com.xiyu.bid.approval.entity.ApprovalRequest;
import com.xiyu.bid.approval.repository.ApprovalActionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 审批详情 DTO 组装器
 */
@Component
@RequiredArgsConstructor
public class ApprovalDetailAssembler {

    private final ApprovalActionRepository actionRepository;

    public ApprovalDetailDTO toDetailDTO(ApprovalRequest request) {
        List<ApprovalActionDTO> actionDTOs = actionRepository.findByApprovalRequestIdOrderByActionTimeAsc(request.getId())
                .stream()
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
}
