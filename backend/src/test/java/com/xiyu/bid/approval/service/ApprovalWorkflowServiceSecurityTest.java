package com.xiyu.bid.approval.service;

import com.xiyu.bid.approval.entity.ApprovalRequest;
import com.xiyu.bid.approval.enums.ApprovalStatus;
import com.xiyu.bid.approval.repository.ApprovalActionRepository;
import com.xiyu.bid.approval.repository.ApprovalRequestRepository;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalWorkflowServiceSecurityTest {

    @Mock
    private ApprovalRequestRepository requestRepository;

    @Mock
    private ApprovalActionRepository actionRepository;

    @Mock
    private ApprovalActionRecorder actionRecorder;

    private ApprovalWorkflowService approvalWorkflowService;

    private ApprovalRequest pendingRequest;

    @BeforeEach
    void setUp() {
        ApprovalQueryService queryService = new ApprovalQueryService(requestRepository, actionRepository);
        ApprovalCommandService commandService = new ApprovalCommandService(requestRepository, actionRecorder, queryService);
        approvalWorkflowService = new ApprovalWorkflowService(commandService, queryService);
        pendingRequest = ApprovalRequest.builder()
                .id(UUID.randomUUID())
                .projectId(100L)
                .projectName("Test Project")
                .approvalType("PROJECT")
                .status(ApprovalStatus.PENDING)
                .requesterId(10L)
                .requesterName("requester")
                .currentApproverId(20L)
                .currentApproverName("approver")
                .priority(1)
                .title("Approval")
                .description("Approval detail")
                .submittedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .isRead(false)
                .build();
    }

    @Test
    void getPendingApprovals_NonPrivilegedUserCanOnlySeeOwnQueue() {
        when(requestRepository.findByStatusAndCurrentApproverIdOrderByPriorityDescCreatedAtDesc(
                ApprovalStatus.PENDING, 20L)).thenReturn(List.of(pendingRequest));
        when(actionRepository.findByApprovalRequestIdOrderByActionTimeAsc(pendingRequest.getId())).thenReturn(List.of());

        var result = approvalWorkflowService.getPendingApprovals(
                20L,
                User.Role.STAFF,
                null,
                PageRequest.of(0, 10)
        );

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(requestRepository).findByStatusAndCurrentApproverIdOrderByPriorityDescCreatedAtDesc(
                ApprovalStatus.PENDING, 20L);
        verify(requestRepository, never()).findByStatusOrderByPriorityDescCreatedAtDesc(ApprovalStatus.PENDING);
    }

    @Test
    void getPendingApprovals_NonPrivilegedUserCannotReadAnotherApproverQueue() {
        assertThatThrownBy(() -> approvalWorkflowService.getPendingApprovals(
                20L,
                User.Role.STAFF,
                99L,
                PageRequest.of(0, 10)
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能查看自己的待审批列表");
    }

    @Test
    void getApprovalDetail_AllowsRequesterToReadOwnRequest() {
        when(requestRepository.findById(pendingRequest.getId())).thenReturn(Optional.of(pendingRequest));
        when(actionRepository.findByApprovalRequestIdOrderByActionTimeAsc(pendingRequest.getId())).thenReturn(List.of());

        var detail = approvalWorkflowService.getApprovalDetail(
                pendingRequest.getId(),
                10L,
                User.Role.STAFF
        );

        assertThat(detail.getId()).isEqualTo(pendingRequest.getId());
    }

    @Test
    void getApprovalDetail_RejectsUnrelatedNonPrivilegedUser() {
        when(requestRepository.findById(pendingRequest.getId())).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> approvalWorkflowService.getApprovalDetail(
                pendingRequest.getId(),
                999L,
                User.Role.STAFF
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权查看该审批");
    }

    @Test
    void getApprovalDetail_AllowsManagersToReadAcrossUsers() {
        when(requestRepository.findById(pendingRequest.getId())).thenReturn(Optional.of(pendingRequest));
        when(actionRepository.findByApprovalRequestIdOrderByActionTimeAsc(pendingRequest.getId())).thenReturn(List.of());

        var detail = approvalWorkflowService.getApprovalDetail(
                pendingRequest.getId(),
                999L,
                User.Role.MANAGER
        );

        assertThat(detail.getId()).isEqualTo(pendingRequest.getId());
    }
}
