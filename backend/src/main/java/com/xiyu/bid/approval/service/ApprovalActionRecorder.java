package com.xiyu.bid.approval.service;

import com.xiyu.bid.approval.entity.ApprovalAction;
import com.xiyu.bid.approval.enums.ApprovalActionType;
import com.xiyu.bid.approval.enums.ApprovalStatus;
import com.xiyu.bid.approval.repository.ApprovalActionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class ApprovalActionRecorder {

    private final ApprovalActionRepository actionRepository;

    void record(UUID requestId,
                ApprovalActionType actionType,
                Long actorId,
                String actorName,
                String comment,
                ApprovalStatus previousStatus,
                ApprovalStatus newStatus) {
        ApprovalAction action = ApprovalAction.builder()
                .approvalRequestId(requestId)
                .actionType(actionType)
                .actorId(actorId)
                .actorName(actorName)
                .comment(comment)
                .actionTime(LocalDateTime.now())
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .build();
        actionRepository.save(action);
    }
}
