// Input: Mockito 桩 FeeRepository / ProjectClosureRepository / ProjectRepository
// Output: 闸门策略 + 持久化 + 409/423/422 行为断言
// Pos: backend test source
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.fees.entity.Fee;
import com.xiyu.bid.fees.repository.FeeRepository;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.dto.ClosureSubmitRequest;
import com.xiyu.bid.project.entity.ProjectClosure;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.project.notification.ProjectNotificationService;
import com.xiyu.bid.project.repository.ProjectClosureRepository;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.project.service.ProjectClosureDepositAssembler;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectClosureServiceTest {

    private ProjectClosureRepository closureRepo;
    private FeeRepository feeRepo;
    private TaskRepository taskRepo;
    private ProjectRepository projectRepo;
    private ProjectStageService stageService;
    private ProjectClosureDepositAssembler depositAssembler;
    private ProjectClosureTaskAssembler taskAssembler;
    private UserRepository userRepository;
    private NotificationApplicationService notificationService;
    private com.xiyu.bid.documentexport.service.DocumentExportService documentExportService;
    private ProjectAccessScopeService projectAccessScopeService;
    private ProjectClosurePermissionGuard closurePermissionGuard;
    private com.xiyu.bid.project.notification.ProjectNotificationService projectNotificationService;
    private ProjectClosureService service;

    private static final Long PID = 1L;
    private static final Long UID = 99L;
    private static final Long DOC = 555L;
    private static final LocalDateTime WHEN = LocalDateTime.of(2026, 5, 1, 10, 0);

    @BeforeEach
    void setup() {
        closureRepo = mock(ProjectClosureRepository.class);
        feeRepo = mock(FeeRepository.class);
        taskRepo = mock(TaskRepository.class);
        projectRepo = mock(ProjectRepository.class);
        stageService = mock(ProjectStageService.class);
        var initiationRepo = mock(ProjectInitiationDetailsRepository.class);
        depositAssembler = new ProjectClosureDepositAssembler(feeRepo, initiationRepo);
        taskAssembler = new ProjectClosureTaskAssembler(taskRepo);
        userRepository = mock(UserRepository.class);
        notificationService = mock(NotificationApplicationService.class);
        documentExportService = mock(com.xiyu.bid.documentexport.service.DocumentExportService.class);
        projectAccessScopeService = mock(ProjectAccessScopeService.class);
        var projectDocumentRepo = mock(com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository.class);
        closurePermissionGuard = mock(ProjectClosurePermissionGuard.class);
        // Guard 默认放行（多数用例不关心审核权）；涉及审核权阻断的用例会单独 stub 抛异常
        lenient().doNothing().when(closurePermissionGuard).assertCanReviewClosure(eq(PID));
        projectNotificationService = mock(com.xiyu.bid.project.notification.ProjectNotificationService.class);
        service = new ProjectClosureService(closureRepo, projectRepo, stageService, depositAssembler, taskAssembler, userRepository, notificationService, documentExportService, projectDocumentRepo, projectAccessScopeService, closurePermissionGuard, mock(com.xiyu.bid.notification.service.NotificationRecipientResolver.class), projectNotificationService);
        // 默认：无任务（即零任务 = 可结项）
        lenient().when(taskRepo.findByProjectId(PID)).thenReturn(List.of());
        Project p = new Project();
        p.setId(PID);
        when(projectRepo.findById(PID)).thenReturn(Optional.of(p));
        when(closureRepo.findByProjectId(PID)).thenReturn(Optional.empty());
        when(closureRepo.existsByProjectIdAndStageLockedTrue(PID)).thenReturn(false);
        when(closureRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(stageService.currentStage(PID)).thenReturn(ProjectStage.RETROSPECTIVE);
    }

    private Fee bond(Fee.Status status) {
        return Fee.builder()
                .id(11L).projectId(PID).feeType(Fee.FeeType.BID_BOND)
                .amount(new BigDecimal("50000")).status(status)
                .feeDate(LocalDateTime.now()).returnDate(status == Fee.Status.RETURNED ? WHEN : null)
                .build();
    }

    // ---------- Preview ----------

    @Test
    void preview_noDeposit_canClose() {
        when(feeRepo.findByProjectIdAndStatus(eq(PID), eq(Fee.Status.RETURNED))).thenReturn(List.of());
        when(feeRepo.findByProjectId(PID)).thenReturn(List.of());
        var dto = service.preview(PID);
        assertTrue(dto.isCanClose());
        assertEquals("NA", dto.getDepositReturnStatus());
        assertTrue(dto.getBlockingReasons().isEmpty());
    }

    /** CO-392: preview 必须先做项目级访问守卫 assertCurrentUserCanAccessProject，
     *  与 ProjectDraftingService.get() 对齐，防止越权查看任意项目结项预览。 */
    @Test
    void co392_preview_invokes_project_access_guard() {
        when(feeRepo.findByProjectIdAndStatus(eq(PID), eq(Fee.Status.RETURNED))).thenReturn(List.of());
        when(feeRepo.findByProjectId(PID)).thenReturn(List.of());
        service.preview(PID);
        verify(projectAccessScopeService).assertCurrentUserCanAccessProject(PID);
    }

    @Test
    void preview_depositNotReturned_cannotClose_withCoreReason() {
        when(feeRepo.findByProjectIdAndStatus(eq(PID), eq(Fee.Status.RETURNED))).thenReturn(List.of());
        when(feeRepo.findByProjectId(PID)).thenReturn(List.of(bond(Fee.Status.PAID)));
        var dto = service.preview(PID);
        assertEquals("NOT_RETURNED", dto.getDepositReturnStatus());
        assertTrue(dto.getBlockingReasons().contains("保证金未退回"));
    }

    @Test
    void preview_depositReturnedButNoEvidence_blocking() {
        when(feeRepo.findByProjectIdAndStatus(eq(PID), eq(Fee.Status.RETURNED))).thenReturn(List.of(bond(Fee.Status.RETURNED)));
        when(feeRepo.findByProjectId(PID)).thenReturn(List.of(bond(Fee.Status.RETURNED)));
        when(closureRepo.findByProjectId(PID)).thenReturn(Optional.empty());
        var dto = service.preview(PID);
        assertEquals("FULLY_RETURNED", dto.getDepositReturnStatus());
        assertTrue(dto.getBlockingReasons().stream().anyMatch(r -> r.contains("凭证")));
    }

    // ---------- Submit happy path ----------

    @Test
    void submit_noDeposit_allowed_persistsReviewPending() {
        when(feeRepo.findByProjectIdAndStatus(eq(PID), eq(Fee.Status.RETURNED))).thenReturn(List.of());
        when(feeRepo.findByProjectId(PID)).thenReturn(List.of());
        var req = ClosureSubmitRequest.builder().archiveLocation("OSS://a").notes("结项").build();
        var dto = service.submitClosure(PID, req, UID);
        assertEquals("PENDING", dto.getReviewStatus());
        assertNotNull(dto.getReviewStatus());
        verify(closureRepo).save(any(ProjectClosure.class));
    }

    @Test
    void submit_depositFullyReturnedWithEvidence_allowed() {
        when(feeRepo.findByProjectIdAndStatus(eq(PID), eq(Fee.Status.RETURNED))).thenReturn(List.of(bond(Fee.Status.RETURNED)));
        when(feeRepo.findByProjectId(PID)).thenReturn(List.of(bond(Fee.Status.RETURNED)));
        var req = ClosureSubmitRequest.builder()
                .depositReturnStatus("FULLY_RETURNED").depositReturnDate(WHEN).depositReturnEvidenceId(DOC)
                .build();
        var dto = service.submitClosure(PID, req, UID);
        assertEquals("PENDING", dto.getReviewStatus());
        assertEquals(DOC, dto.getDepositReturnEvidenceId());
    }

    // ---------- Submit deny paths (PRD 核心闸门) ----------

    @Test
    void submit_depositNotReturned_throws409() {
        when(feeRepo.findByProjectIdAndStatus(eq(PID), eq(Fee.Status.RETURNED))).thenReturn(List.of());
        when(feeRepo.findByProjectId(PID)).thenReturn(List.of(bond(Fee.Status.PAID)));
        var req = ClosureSubmitRequest.builder().build();
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.submitClosure(PID, req, UID));
        assertEquals(409, ex.getStatusCode().value());
        assertTrue(ex.getReason() != null && ex.getReason().contains("保证金未退回"));
        verify(closureRepo, never()).save(any());
    }

    @Test
    void submit_depositReturned_missingEvidence_throws409() {
        // 保证金在 fees 已全部 RETURNED，但请求中未指定退回状态→ resolveDepositStatus 返回 NOT_RETURNED
        when(feeRepo.findByProjectIdAndStatus(eq(PID), eq(Fee.Status.RETURNED))).thenReturn(List.of(bond(Fee.Status.RETURNED)));
        when(feeRepo.findByProjectId(PID)).thenReturn(List.of(bond(Fee.Status.RETURNED)));
        when(closureRepo.findByProjectId(PID)).thenReturn(Optional.empty());
        var req = ClosureSubmitRequest.builder().build();
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.submitClosure(PID, req, UID));
        assertEquals(409, ex.getStatusCode().value());
        assertTrue(ex.getReason() != null && ex.getReason().contains("保证金未退回"));
    }

    @Test
    void submit_alreadyClosed_throws423() {
        var closedClosure = new ProjectClosure();
        closedClosure.setStageLocked(true);
        when(closureRepo.findByProjectId(PID)).thenReturn(Optional.of(closedClosure));
        var req = ClosureSubmitRequest.builder().build();
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.submitClosure(PID, req, UID));
        assertEquals(423, ex.getStatusCode().value());
        verify(closureRepo, never()).save(any());
    }

    @Test
    @DisplayName("CO-572: PENDING 状态下重复提交应返回 409")
    void submit_whenPending_throws409() {
        var pendingClosure = new ProjectClosure();
        pendingClosure.setReviewStatus("PENDING");
        when(closureRepo.findByProjectId(PID)).thenReturn(Optional.of(pendingClosure));
        var req = ClosureSubmitRequest.builder().build();
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.submitClosure(PID, req, UID));
        assertEquals(409, ex.getStatusCode().value());
        assertTrue(ex.getReason() != null && ex.getReason().contains("待审核"));
        verify(closureRepo, never()).save(any());
    }

    @Test
    void submit_projectNotFound_throws() {
        when(projectRepo.findById(PID)).thenReturn(Optional.empty());
        var req = ClosureSubmitRequest.builder().build();
        assertThrows(RuntimeException.class,
                () -> service.submitClosure(PID, req, UID));
    }

    @Test
    void preview_alreadyClosed_cannotClose() {
        when(feeRepo.findByProjectIdAndStatus(eq(PID), eq(Fee.Status.RETURNED))).thenReturn(List.of());
        when(feeRepo.findByProjectId(PID)).thenReturn(List.of());
        when(closureRepo.existsByProjectIdAndStageLockedTrue(PID)).thenReturn(true);
        var dto = service.preview(PID);
        org.junit.jupiter.api.Assertions.assertFalse(dto.isCanClose());
        assertTrue(Boolean.TRUE.equals(dto.getAlreadyClosed()));
    }

    // ---------- C3: 部分退回不能误判 RETURNED ----------

    private Fee bondWithId(Long id, Fee.Status status) {
        return Fee.builder()
                .id(id).projectId(PID).feeType(Fee.FeeType.BID_BOND)
                .amount(new BigDecimal("50000")).status(status)
                .feeDate(LocalDateTime.now()).returnDate(status == Fee.Status.RETURNED ? WHEN : null)
                .build();
    }

    @Test
    void preview_partiallyReturnedDeposit_isNotReturned_notRETURNED() {
        // 2 个 BID_BOND fee；只有 1 个已 RETURNED → 整体应视作 NOT_RETURNED
        when(feeRepo.findByProjectId(PID)).thenReturn(List.of(
                bondWithId(11L, Fee.Status.RETURNED),
                bondWithId(12L, Fee.Status.PAID)));
        var dto = service.preview(PID);
        assertEquals("NOT_RETURNED", dto.getDepositReturnStatus());
        assertTrue(dto.getBlockingReasons().contains("保证金未退回"));
    }

    @Test
    void submit_partiallyReturnedDeposit_throws409_保证金未退回() {
        when(feeRepo.findByProjectId(PID)).thenReturn(List.of(
                bondWithId(11L, Fee.Status.RETURNED),
                bondWithId(12L, Fee.Status.PAID)));
        var req = ClosureSubmitRequest.builder().build();
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.submitClosure(PID, req, UID));
        assertEquals(409, ex.getStatusCode().value());
        assertTrue(ex.getReason() != null && ex.getReason().contains("保证金未退回"));
        verify(closureRepo, never()).save(any());
    }

    // ---------- H1: depositReturnStatus=NOT_RETURNED 必须强制 NOT_RETURNED ----------

    @Test
    void submit_explicitNotReturned_overridesFeesRETURNED_blocks409() {
        // fees 派生 FULLY_RETURNED，但用户提交时显式声明 NOT_RETURNED → 必须按未退回拒绝
        when(feeRepo.findByProjectId(PID)).thenReturn(List.of(bond(Fee.Status.RETURNED)));
        var req = ClosureSubmitRequest.builder().depositReturnStatus("NOT_RETURNED").build();
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.submitClosure(PID, req, UID));
        assertEquals(409, ex.getStatusCode().value());
        assertTrue(ex.getReason() != null && ex.getReason().contains("保证金未退回"));
        verify(closureRepo, never()).save(any());
    }

    // ---------- exportDocuments（一键导出文档）----------

    @Test
    void exportDocuments_closureNotFound_throws404() {
        when(closureRepo.findByProjectId(PID)).thenReturn(Optional.empty());
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.exportDocuments(PID, UID));
        assertEquals(404, ex.getStatusCode().value());
        assertTrue(ex.getReason() != null && ex.getReason().contains("未找到项目结项申请"));
    }

    @Test
    void exportDocuments_closureNotApproved_throws409() {
        ProjectClosure closure = new ProjectClosure();
        closure.setProjectId(PID);
        closure.setReviewStatus("PENDING");
        closure.setStageLocked(false);
        when(closureRepo.findByProjectId(PID)).thenReturn(Optional.of(closure));

        var ex = assertThrows(ResponseStatusException.class,
                () -> service.exportDocuments(PID, UID));
        assertEquals(409, ex.getStatusCode().value());
        assertTrue(ex.getReason() != null && ex.getReason().contains("结项审批通过后才能进行结项归档导出"));
    }

    @Test
    void exportDocuments_approved_callsDocumentExportService() {
        ProjectClosure closure = new ProjectClosure();
        closure.setProjectId(PID);
        closure.setReviewStatus("APPROVED");
        closure.setStageLocked(true);
        when(closureRepo.findByProjectId(PID)).thenReturn(Optional.of(closure));

        com.xiyu.bid.entity.User user = new com.xiyu.bid.entity.User();
        user.setId(UID);
        user.setFullName("测试用户");
        when(userRepository.findById(UID)).thenReturn(Optional.of(user));

        var exportDto = com.xiyu.bid.documentexport.dto.DocumentExportDTO.builder()
                .id(999L).projectId(PID).format("json").build();
        when(documentExportService.createExport(eq(PID), any())).thenReturn(exportDto);

        var result = service.exportDocuments(PID, UID);

        assertNotNull(result);
        assertEquals(999L, result.getId());
        assertEquals("json", result.getFormat());
        verify(documentExportService).createExport(eq(PID), any(com.xiyu.bid.documentexport.dto.DocumentExportCreateRequest.class));
    }

    @Test
    void exportDocuments_stageLockedButNotApproved_callsDocumentExportService() {
        // stageLocked=true 但 reviewStatus 非 APPROVED（如已结项但审批记录丢失）也能导出
        ProjectClosure closure = new ProjectClosure();
        closure.setProjectId(PID);
        closure.setReviewStatus("DRAFT");
        closure.setStageLocked(true);
        when(closureRepo.findByProjectId(PID)).thenReturn(Optional.of(closure));

        var exportDto = com.xiyu.bid.documentexport.dto.DocumentExportDTO.builder()
                .id(888L).projectId(PID).format("json").build();
        when(documentExportService.createExport(eq(PID), any())).thenReturn(exportDto);

        var result = service.exportDocuments(PID, UID);

        assertNotNull(result);
        assertEquals(888L, result.getId());
    }

    // ---------- CO-403 纠偏：审核权编排守卫（职责分离 + 项目级投标辅助匹配）----------

    /** approveClosure 必须先调用审核权守卫，再查 closure。 */
    @Test
    void co403_approve_invokes_review_permission_guard() {
        ProjectClosure closure = new ProjectClosure();
        closure.setProjectId(PID);
        closure.setReviewStatus("PENDING");
        when(closureRepo.findByProjectId(PID)).thenReturn(Optional.of(closure));

        service.approveClosure(PID, UID);

        verify(closurePermissionGuard).assertCanReviewClosure(PID);
    }

    /** 守卫拒绝（如提交人审核自己）→ approveClosure 必须 403 传播，不修改 closure。 */
    @Test
    void co403_approve_guardDenies_throws403_andDoesNotPersist() {
        // 模拟 Guard 拦截：提交人(投标项目负责人)审核自己提交的结项
        org.mockito.Mockito.doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "不可审核自己提交的结项"))
                .when(closurePermissionGuard).assertCanReviewClosure(PID);

        var ex = assertThrows(ResponseStatusException.class,
                () -> service.approveClosure(PID, UID));
        assertEquals(403, ex.getStatusCode().value());
        verify(closureRepo, never()).save(any());
    }

    /** rejectClosure 同样必须先调用审核权守卫。 */
    @Test
    void co403_reject_invokes_review_permission_guard() {
        ProjectClosure closure = new ProjectClosure();
        closure.setProjectId(PID);
        closure.setReviewStatus("PENDING");
        when(closureRepo.findByProjectId(PID)).thenReturn(Optional.of(closure));

        service.rejectClosure(PID, "内容不全", UID);

        verify(closurePermissionGuard).assertCanReviewClosure(PID);
    }

    /** 守卫拒绝 → rejectClosure 必须 403 传播。 */
    @Test
    void co403_reject_guardDenies_throws403() {
        org.mockito.Mockito.doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "无权审核"))
                .when(closurePermissionGuard).assertCanReviewClosure(PID);

        var ex = assertThrows(ResponseStatusException.class,
                () -> service.rejectClosure(PID, "原因", UID));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void preview_failedBidProject_stageClosed_alreadyClosedTrue() {
        // 流标项目：stage=CLOSED 但无 closure 审批记录
        when(feeRepo.findByProjectIdAndStatus(eq(PID), eq(Fee.Status.RETURNED))).thenReturn(List.of());
        when(feeRepo.findByProjectId(PID)).thenReturn(List.of());
        // 模拟 project.stage = CLOSED（流标项目直接推进到 CLOSED，无 closure 审批记录）
        Project closedProject = new Project();
        closedProject.setId(PID);
        closedProject.setStage("CLOSED");
        when(projectRepo.findById(PID)).thenReturn(Optional.of(closedProject));
        when(stageService.currentStage(PID)).thenReturn(ProjectStage.CLOSED);
        var dto = service.preview(PID);
        assertTrue(dto.getAlreadyClosed());
    }

    // ---------- Task Completion Gate ----------

    private Task task(Task.Status status) {
        Task t = new Task();
        t.setStatus(status);
        return t;
    }

    @Test
    void preview_noTasks_taskCountsZero() {
        when(feeRepo.findByProjectId(PID)).thenReturn(List.of());
        when(taskRepo.findByProjectId(PID)).thenReturn(List.of());
        var dto = service.preview(PID);
        assertEquals(0, dto.getTotalTaskCount());
        assertEquals(0, dto.getCompletedTaskCount());
        assertEquals(0, dto.getIncompleteTaskCount());
    }

    @Test
    void preview_allTasksCompleted_canClose() {
        when(feeRepo.findByProjectId(PID)).thenReturn(List.of());
        when(taskRepo.findByProjectId(PID)).thenReturn(List.of(
                task(Task.Status.COMPLETED), task(Task.Status.COMPLETED)));
        var dto = service.preview(PID);
        assertEquals(2, dto.getTotalTaskCount());
        assertEquals(2, dto.getCompletedTaskCount());
        assertEquals(0, dto.getIncompleteTaskCount());
        assertTrue(dto.isCanClose());
        assertTrue(dto.getBlockingReasons().isEmpty());
    }

    @Test
    void preview_incompleteTasks_cannotClose_withReason() {
        when(feeRepo.findByProjectId(PID)).thenReturn(List.of());
        when(taskRepo.findByProjectId(PID)).thenReturn(List.of(
                task(Task.Status.TODO), task(Task.Status.COMPLETED)));
        var dto = service.preview(PID);
        assertEquals(2, dto.getTotalTaskCount());
        assertEquals(1, dto.getCompletedTaskCount());
        assertEquals(1, dto.getIncompleteTaskCount());
        assertFalse(dto.isCanClose());
        assertTrue(dto.getBlockingReasons().stream()
                .anyMatch(r -> r.contains("未完成的任务")));
    }

    @Test
    void submit_incompleteTasks_throws409() {
        when(feeRepo.findByProjectId(PID)).thenReturn(List.of());
        when(taskRepo.findByProjectId(PID)).thenReturn(List.of(task(Task.Status.TODO)));
        var req = ClosureSubmitRequest.builder()
                .depositReturnStatus("NA").archiveLocation("/archive")
                .projectSummary("summary").build();
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.submitClosure(PID, req, UID));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void approve_incompleteTasks_throws409() {
        var closure = ProjectClosure.builder()
                .projectId(PID).reviewStatus("PENDING")
                .depositReturnStatus("NA").createdBy(UID).build();
        when(closureRepo.findByProjectId(PID)).thenReturn(Optional.of(closure));
        when(taskRepo.findByProjectId(PID)).thenReturn(List.of(task(Task.Status.TODO)));
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.approveClosure(PID, UID));
        assertEquals(409, ex.getStatusCode().value());
    }
}
