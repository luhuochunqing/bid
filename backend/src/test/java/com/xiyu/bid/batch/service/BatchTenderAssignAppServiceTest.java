package com.xiyu.bid.batch.service;

import com.xiyu.bid.audit.service.IAuditLogService;
import com.xiyu.bid.batch.core.TenderStatusTransitionPolicy;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.batch.dto.BatchTenderAssignRequest;
import com.xiyu.bid.batch.entity.TenderAssignmentRecord;
import com.xiyu.bid.batch.repository.TenderAssignmentRecordRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchTenderAssignAppServiceTest {

    private TenderRepository tenderRepository;
    private ProjectRepository projectRepository;
    private UserRepository userRepository;
    private TenderAssignmentRecordRepository assignmentRecordRepository;
    private ProjectAccessScopeService projectAccessScopeService;
    private BatchTenderAssignAppService service;

    @BeforeEach
    void setUp() {
        tenderRepository = mock(TenderRepository.class);
        projectRepository = mock(ProjectRepository.class);
        userRepository = mock(UserRepository.class);
        assignmentRecordRepository = mock(TenderAssignmentRecordRepository.class);
        projectAccessScopeService = mock(ProjectAccessScopeService.class);
        IAuditLogService auditLogService = mock(IAuditLogService.class);
        BatchProjectAccessGuard projectAccessGuard = new BatchProjectAccessGuard(projectAccessScopeService, projectRepository);
        BatchTenderAssignmentSupport assignmentSupport =
                new BatchTenderAssignmentSupport(userRepository, assignmentRecordRepository);
        NotificationApplicationService notificationAppService = mock(NotificationApplicationService.class);
        service = new BatchTenderAssignAppService(
                tenderRepository,
                projectAccessGuard,
                assignmentSupport,
                new BatchOperationLogService(auditLogService),
                new TenderStatusTransitionPolicy(),
                notificationAppService
        );
    }

    @Test
    void shouldAssignTrackingTendersAndPersistAssignmentRecords() {
        User assignee = User.builder().id(9L).fullName("销售甲").build();
        User currentUser = User.builder().id(1L).fullName("经理乙").build();
        Tender pending = Tender.builder().id(1L).status(Tender.Status.PENDING_ASSIGNMENT).build();
        Tender tracking = Tender.builder().id(2L).status(Tender.Status.TRACKING).build();

        when(userRepository.findById(9L)).thenReturn(Optional.of(assignee));
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(pending));
        when(tenderRepository.findById(2L)).thenReturn(Optional.of(tracking));

        BatchTenderAssignRequest request = new BatchTenderAssignRequest();
        request.setTenderIds(List.of(1L, 2L));
        request.setAssigneeId(9L);
        request.setRemark("follow-up");

        var response = service.batchAssign(request, currentUser);

        assertTrue(response.getSuccess());
        assertEquals(2, response.getSuccessCount());
        assertEquals(Tender.Status.TRACKING, pending.getStatus());
        verify(assignmentRecordRepository).saveAll(anyList());
    }

    @Test
    void shouldRejectAssigningBiddedTenderBackToTracking() {
        User assignee = User.builder().id(9L).fullName("销售甲").build();
        Tender bidded = Tender.builder().id(1L).status(Tender.Status.BIDDING).build();

        when(userRepository.findById(9L)).thenReturn(Optional.of(assignee));
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(bidded));

        BatchTenderAssignRequest request = new BatchTenderAssignRequest();
        request.setTenderIds(List.of(1L));
        request.setAssigneeId(9L);

        var response = service.batchAssign(request, null);

        assertFalse(response.getSuccess());
        assertEquals(1, response.getFailureCount());
        assertEquals("INVALID_STATUS_TRANSITION", response.getErrors().get(0).getErrorCode());
    }

    @Test
    void shouldRejectTenderLinkedToProjectOutsideDataScope() {
        User assignee = User.builder().id(9L).fullName("销售甲").build();
        User currentUser = User.builder().id(1L).fullName("经理乙").build();
        Tender pending = Tender.builder().id(1L).status(Tender.Status.PENDING_ASSIGNMENT).build();
        Project project = Project.builder().id(10L).tenderId(1L).build();

        when(userRepository.findById(9L)).thenReturn(Optional.of(assignee));
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(pending));
        when(projectRepository.findByTenderId(1L)).thenReturn(List.of(project));
        doThrow(new org.springframework.security.access.AccessDeniedException("权限不足"))
                .when(projectAccessScopeService).assertCurrentUserCanAccessProject(10L);

        BatchTenderAssignRequest request = new BatchTenderAssignRequest();
        request.setTenderIds(List.of(1L));
        request.setAssigneeId(9L);

        var response = service.batchAssign(request, currentUser);

        assertFalse(response.getSuccess());
        assertEquals(1, response.getFailureCount());
        assertEquals("PERMISSION_DENIED", response.getErrors().get(0).getErrorCode());
        verify(tenderRepository, never()).saveAll(anyList());
        verify(assignmentRecordRepository, never()).saveAll(anyList());
    }
}
