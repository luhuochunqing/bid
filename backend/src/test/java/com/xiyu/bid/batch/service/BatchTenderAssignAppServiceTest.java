package com.xiyu.bid.batch.service;

import com.xiyu.bid.batch.core.TenderStatusTransitionPolicy;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.batch.dto.BatchTenderAssignRequest;
import com.xiyu.bid.batch.entity.TenderAssignmentRecord;
import com.xiyu.bid.batch.repository.TenderAssignmentRecordRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.service.ProjectManagerDepartmentEnricher;
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
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private ProjectManagerDepartmentEnricher departmentEnricher;
    private BatchTenderAssignAppService service;

    @BeforeEach
    void setUp() {
        tenderRepository = mock(TenderRepository.class);
        projectRepository = mock(ProjectRepository.class);
        userRepository = mock(UserRepository.class);
        assignmentRecordRepository = mock(TenderAssignmentRecordRepository.class);
        projectAccessScopeService = mock(ProjectAccessScopeService.class);
        departmentEnricher = mock(ProjectManagerDepartmentEnricher.class);
        BatchProjectAccessGuard projectAccessGuard = new BatchProjectAccessGuard(projectAccessScopeService, projectRepository);
        BatchTenderAssignmentSupport assignmentSupport =
                new BatchTenderAssignmentSupport(userRepository, assignmentRecordRepository);
        NotificationApplicationService notificationAppService = mock(NotificationApplicationService.class);
        service = new BatchTenderAssignAppService(
                tenderRepository,
                projectAccessGuard,
                assignmentSupport,
                notificationAppService,
                mock(com.xiyu.bid.tender.service.TenderAuditService.class),
                departmentEnricher
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

    /**
     * CO-537 根因修复：Tender.department 写入时必须通过 enricher 反查部门名，
     * 不能用 User.getDepartmentName()（生产环境多为空字符串）。
     *
     * <p>链路：userId → user.department_code（OSS external_dept_id）→ organization_departments.department_name
     */
    @Test
    void shouldPersistDepartmentViaEnricherNotUserDepartmentName() {
        // Given: assignee.departmentName 为空（模拟生产环境），但 enricher 能反查到部门名
        User assignee = User.builder().id(9L).fullName("销售甲").departmentName("").build();
        User currentUser = User.builder().id(1L).fullName("经理乙").build();
        Tender pending = Tender.builder().id(1L).status(Tender.Status.PENDING_ASSIGNMENT).build();

        when(userRepository.findById(9L)).thenReturn(Optional.of(assignee));
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(pending));
        when(departmentEnricher.resolveDepartmentNameByUserId(9L)).thenReturn("研发中心");

        BatchTenderAssignRequest request = new BatchTenderAssignRequest();
        request.setTenderIds(List.of(1L));
        request.setAssigneeId(9L);

        service.batchAssign(request, currentUser);

        // Then: tender.department 应为 enricher 反查结果，而非 user.departmentName（空字符串）
        assertEquals("研发中心", pending.getDepartment());
        verify(departmentEnricher).resolveDepartmentNameByUserId(9L);
    }

    @Test
    void shouldKeepDepartmentNullWhenEnricherReturnsNull() {
        // 边界场景：enricher 也查不到部门（user.departmentCode 为空），department 应保持 null
        User assignee = User.builder().id(9L).fullName("销售甲").departmentName("").build();
        User currentUser = User.builder().id(1L).fullName("经理乙").build();
        Tender pending = Tender.builder().id(1L).status(Tender.Status.PENDING_ASSIGNMENT).build();

        when(userRepository.findById(9L)).thenReturn(Optional.of(assignee));
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(pending));
        when(departmentEnricher.resolveDepartmentNameByUserId(9L)).thenReturn(null);

        BatchTenderAssignRequest request = new BatchTenderAssignRequest();
        request.setTenderIds(List.of(1L));
        request.setAssigneeId(9L);

        service.batchAssign(request, currentUser);

        // Then: department 为 null（enricher 查不到，不强制写入）
        assertNull(pending.getDepartment());
    }
}
