package com.xiyu.bid.tender.service;

import com.xiyu.bid.batch.core.TenderStatusTransitionPolicy;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.dto.ProjectDTO;
import com.xiyu.bid.project.service.ProjectService;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.task.dto.TaskDTO;
import com.xiyu.bid.task.service.TaskService;
import com.xiyu.bid.tender.controller.TenderEvaluationController.TenderBidResult;
import com.xiyu.bid.tender.dto.TenderReviewRequest;
import com.xiyu.bid.tender.entity.TenderEvaluation;
import com.xiyu.bid.tender.entity.TenderEvaluationBasic;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenderEvaluationServiceTest {

    @Mock
    private TenderEvaluationRepository tenderEvaluationRepository;

    @Mock
    private TenderRepository tenderRepository;

    @Mock
    private ProjectService projectService;

    @Mock
    private TaskService taskService;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenderAssignmentPermissions permissions;

    @Mock
    private TenderProjectAccessGuard accessGuard;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Mock
    private TenderEvaluationDocumentService tenderEvaluationDocumentService;

    @Mock
    private InitiationPrefillService initiationPrefillService;

    @Mock
    private TenderBidTaskFactory bidTaskFactory;

    private TenderEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new TenderEvaluationService(
                tenderEvaluationRepository,
                tenderRepository,
                projectService,
                taskService,
                taskRepository,
                userRepository,
                mock(TenderEvaluationSubmissionService.class),
                permissions,
                accessGuard,
                eventPublisher,
                tenderEvaluationDocumentService,
                initiationPrefillService,
                bidTaskFactory
        );
        // 决策类端点默认放行；individual 测试覆写为 false 检验 403 路径。
        org.mockito.Mockito.lenient()
                .when(permissions.canDecide(any(), any()))
                .thenReturn(true);
    }

    @Test
    @DisplayName("审核通过后标讯进入投标中状态")
    void reviewTender_ShouldMoveTenderToBiddingWhenApproved() {
        Tender tender = Tender.builder()
                .id(1L)
                .title("测试标讯")
                .status(Tender.Status.EVALUATED)
                .build();
        TenderEvaluation evaluation = TenderEvaluation.builder()
                .tenderId(1L)
                .reviewStatus(TenderEvaluation.ReviewStatus.PENDING)
                .build();
        User reviewer = User.builder().id(9L).username("admin-reviewer").build();

        when(tenderEvaluationRepository.findByTenderId(1L)).thenReturn(Optional.of(evaluation));
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));
        when(userRepository.findById(9L)).thenReturn(Optional.of(reviewer));
        when(tenderEvaluationRepository.save(any(TenderEvaluation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tenderRepository.save(any(Tender.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.reviewTender(1L, new TenderReviewRequest(true, null, "通过"), 9L);

        assertThat(tender.getStatus()).isEqualTo(Tender.Status.BIDDING);
        verify(tenderRepository).save(argThat(saved -> saved.getStatus() == Tender.Status.BIDDING));
    }

    @Test
    @DisplayName("仅允许投标中标讯发起立项")
    void proceedToBid_ShouldRequireBiddingStatusAndCreateArtifacts() {
        Tender tender = Tender.builder()
                .id(1L)
                .title("测试标讯")
                .status(Tender.Status.BIDDING)
                .industry("制造业")
                .region("上海")
                .purchaserName("测试采购方")
                .description("需要创建立项")
                .deadline(LocalDateTime.of(2026, 5, 20, 10, 0))
                .build();
        TenderEvaluation evaluation = TenderEvaluation.builder()
                .tenderId(1L)
                .evaluatorId(18L)
                .build();

        when(tenderEvaluationRepository.findByTenderId(1L)).thenReturn(Optional.of(evaluation));
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));
        when(projectService.createProject(any(ProjectDTO.class))).thenReturn(ProjectDTO.builder()
                .id(101L)
                .name("测试标讯")
                .status(Project.Status.PENDING_INITIATION)
                .build());
        when(bidTaskFactory.reuseOrCreate(any(), any(), any(), any())).thenReturn(TaskDTO.builder()
                .id(202L)
                .title("【待立项】测试标讯")
                .status(Task.Status.TODO)
                .build());

        TenderBidResult result = service.proceedToBid(1L, 99L);

        assertThat(result.projectId()).isEqualTo(101L);
        assertThat(result.taskId()).isEqualTo(202L);
        verify(projectService).createProject(argThat(project ->
                project.getTenderId().equals(1L)
                        && project.getManagerId().equals(18L)
                        && project.getStatus() == Project.Status.PENDING_INITIATION
        ));
        verify(bidTaskFactory).reuseOrCreate(eq(1L), eq(101L), eq("测试标讯"), eq(18L));
    }

    @Test
    @DisplayName("快速投标无评估表时以当前操作人作为项目经理")
    void proceedToBid_WithoutEvaluation_UsesCurrentUserAsManager() {
        Tender tender = Tender.builder()
                .id(1L)
                .title("测试标讯")
                .status(Tender.Status.BIDDING)
                .industry("制造业")
                .region("上海")
                .purchaserName("测试采购方")
                .description("需要创建立项")
                .deadline(LocalDateTime.of(2026, 5, 20, 10, 0))
                .build();

        when(tenderEvaluationRepository.findByTenderId(1L)).thenReturn(Optional.empty());
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));
        when(projectService.createProject(any(ProjectDTO.class))).thenReturn(ProjectDTO.builder()
                .id(101L)
                .name("测试标讯")
                .status(Project.Status.PENDING_INITIATION)
                .build());
        when(bidTaskFactory.reuseOrCreate(any(), any(), any(), any())).thenReturn(TaskDTO.builder()
                .id(202L)
                .title("【待立项】测试标讯")
                .status(Task.Status.TODO)
                .build());

        TenderBidResult result = service.proceedToBid(1L, 99L);

        assertThat(result.projectId()).isEqualTo(101L);
        assertThat(result.taskId()).isEqualTo(202L);
        verify(projectService).createProject(argThat(project ->
                project.getTenderId().equals(1L)
                        && project.getManagerId().equals(99L)
                        && project.getStatus() == Project.Status.PENDING_INITIATION
        ));
        verify(bidTaskFactory).reuseOrCreate(eq(1L), eq(101L), eq("测试标讯"), eq(99L));
    }

    @Test
    @DisplayName("非投标中状态禁止发起立项")
    void proceedToBid_ShouldRejectNonBiddingTender() {
        Tender tender = Tender.builder()
                .id(1L)
                .title("测试标讯")
                .status(Tender.Status.EVALUATED)
                .build();

        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));

        assertThrows(IllegalStateException.class, () -> service.proceedToBid(1L, 99L));
    }

    @Test
    @DisplayName("reviewTender: 非分配人（canDecide=false）抛 AccessDeniedException")
    void reviewTender_nonAssigner_throwsForbidden() {
        Tender tender = Tender.builder().id(1L).status(Tender.Status.EVALUATED).build();
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));
        when(permissions.canDecide(1L, 999L)).thenReturn(false);

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.reviewTender(1L, new TenderReviewRequest(true, null, "通过"), 999L));

        verify(tenderEvaluationRepository, org.mockito.Mockito.never()).save(any());
        verify(tenderRepository, org.mockito.Mockito.never()).save(any(Tender.class));
    }

    @Test
    @DisplayName("proceedToBid: 非分配人（canDecide=false）抛 AccessDeniedException")
    void proceedToBid_nonAssigner_throwsForbidden() {
        Tender tender = Tender.builder().id(1L).status(Tender.Status.BIDDING).build();
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));
        when(permissions.canDecide(1L, 999L)).thenReturn(false);

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.proceedToBid(1L, 999L));

        verify(projectService, org.mockito.Mockito.never()).createProject(any());
        verify(bidTaskFactory, org.mockito.Mockito.never()).reuseOrCreate(any(), any(), any(), any());
    }

    @Test
    @DisplayName("reviewTender: 项目作用域拒绝（accessGuard） → AccessDeniedException")
    void reviewTender_projectScopeDenied_throwsForbidden() {
        Tender tender = Tender.builder().id(1L).status(Tender.Status.EVALUATED).build();
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));
        org.mockito.Mockito.doThrow(new org.springframework.security.access.AccessDeniedException("project scope"))
                .when(accessGuard).assertCanAccessTender(tender);

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.reviewTender(1L, new TenderReviewRequest(true, null, "通过"), 9L));

        verify(tenderEvaluationRepository, org.mockito.Mockito.never()).save(any());
    }

    // ---------- CO-262 P0-1: reviewTender / getEvaluation 返回的 DTO 必须包含 GAP 附件 ----------

    @Test
    @DisplayName("CO-262 P0-1: reviewTender 返回的 DTO 必须填充 projectPlanGapFiles")
    void reviewTender_returnsDtoWithGapFiles() {
        Tender tender = Tender.builder()
                .id(1L)
                .title("测试标讯")
                .status(Tender.Status.EVALUATED)
                .build();
        TenderEvaluation evaluation = TenderEvaluation.builder()
                .tenderId(1L)
                .reviewStatus(TenderEvaluation.ReviewStatus.PENDING)
                .basic(TenderEvaluationBasic.builder().id(1L).plannedShortlistedCount(1).build())
                .build();
        User reviewer = User.builder().id(9L).username("admin-reviewer").build();

        when(tenderEvaluationRepository.findByTenderId(1L)).thenReturn(Optional.of(evaluation));
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));
        when(userRepository.findById(9L)).thenReturn(Optional.of(reviewer));
        when(tenderEvaluationRepository.save(any(TenderEvaluation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tenderRepository.save(any(Tender.class))).thenAnswer(inv -> inv.getArgument(0));
        // 模拟 project_documents 表有 GAP 附件
        com.xiyu.bid.projectworkflow.entity.ProjectDocument gapDoc =
                com.xiyu.bid.projectworkflow.entity.ProjectDocument.builder()
                        .id(200L).projectId(1L)
                        .name("GAP附件")
                        .fileUrl("https://crm.example.com/gap.pdf")
                        .linkedEntityType("EVALUATION_GAP")
                        .linkedEntityId(1L)
                        .build();
        when(tenderEvaluationDocumentService.getDocuments(1L))
                .thenReturn(java.util.List.of(gapDoc));

        com.xiyu.bid.tender.dto.TenderEvaluationDTO dto = service.reviewTender(
                1L, new TenderReviewRequest(true, null, "通过"), 9L);

        assertThat(dto).isNotNull();
        assertThat(dto.evaluationBasic()).isNotNull();
        assertThat(dto.evaluationBasic().projectPlanGapFiles()).hasSize(1);
        assertThat(dto.evaluationBasic().projectPlanGapFiles().get(0).fileName()).isEqualTo("GAP附件");
        assertThat(dto.evaluationBasic().projectPlanGapFiles().get(0).fileUrl())
                .isEqualTo("https://crm.example.com/gap.pdf");
    }

    @Test
    @DisplayName("CO-262 P0-1: getEvaluation 返回的 DTO 必须填充 projectPlanGapFiles")
    void getEvaluation_returnsDtoWithGapFiles() {
        Tender tender = Tender.builder().id(1L).title("测试标讯").build();
        TenderEvaluation evaluation = TenderEvaluation.builder()
                .tenderId(1L)
                .basic(TenderEvaluationBasic.builder().id(1L).plannedShortlistedCount(1).build())
                .build();

        when(tenderEvaluationRepository.findByTenderId(1L)).thenReturn(Optional.of(evaluation));
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));
        com.xiyu.bid.projectworkflow.entity.ProjectDocument gapDoc =
                com.xiyu.bid.projectworkflow.entity.ProjectDocument.builder()
                        .id(200L).projectId(1L)
                        .name("GAP附件")
                        .fileUrl("https://crm.example.com/gap.pdf")
                        .linkedEntityType("EVALUATION_GAP")
                        .linkedEntityId(1L)
                        .build();
        when(tenderEvaluationDocumentService.getDocuments(1L))
                .thenReturn(java.util.List.of(gapDoc));

        Optional<com.xiyu.bid.tender.dto.TenderEvaluationDTO> dto = service.getEvaluation(1L);

        assertThat(dto).isPresent();
        assertThat(dto.get().evaluationBasic()).isNotNull();
        assertThat(dto.get().evaluationBasic().projectPlanGapFiles()).hasSize(1);
        assertThat(dto.get().evaluationBasic().projectPlanGapFiles().get(0).fileName()).isEqualTo("GAP附件");
    }

    @Test
    @DisplayName("CO-333: 标讯有项目负责人时，proceedToBid 用标讯项目负责人作为 project.managerId")
    void proceedToBid_WithTenderProjectManager_UsesTenderProjectManagerAsManager() {
        // CO-333: 标讯里显示的项目负责人是谁，谁就有项目查看和提交立项的权限
        Tender tender = Tender.builder()
                .id(1L)
                .title("测试标讯")
                .status(Tender.Status.BIDDING)
                .industry("制造业")
                .region("上海")
                .purchaserName("测试采购方")
                .projectManagerId(50L)  // 标讯项目负责人
                .projectManagerName("张三")
                .description("需要创建立项")
                .deadline(LocalDateTime.of(2026, 5, 20, 10, 0))
                .build();
        TenderEvaluation evaluation = TenderEvaluation.builder()
                .tenderId(1L)
                .evaluatorId(18L)  // 评估人（不是项目负责人）
                .build();

        when(tenderEvaluationRepository.findByTenderId(1L)).thenReturn(Optional.of(evaluation));
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));
        when(projectService.createProject(any(ProjectDTO.class))).thenReturn(ProjectDTO.builder()
                .id(101L)
                .name("测试标讯")
                .status(Project.Status.PENDING_INITIATION)
                .build());
        when(bidTaskFactory.reuseOrCreate(any(), any(), any(), any())).thenReturn(TaskDTO.builder()
                .id(202L)
                .title("【待立项】测试标讯")
                .status(Task.Status.TODO)
                .build());

        service.proceedToBid(1L, 99L);

        // CO-333: managerId 应该是标讯项目负责人(50L)，不是评估人(18L)或操作人(99L)
        verify(projectService).createProject(argThat(project ->
                project.getManagerId().equals(50L)
        ));
        verify(bidTaskFactory).reuseOrCreate(eq(1L), eq(101L), eq("测试标讯"), eq(50L));
    }

    @Test
    @DisplayName("CO-333: 标讯无项目负责人但有评估人时，回退用评估人作为 project.managerId")
    void proceedToBid_WithoutTenderProjectManager_FallsBackToEvaluator() {
        Tender tender = Tender.builder()
                .id(1L)
                .title("测试标讯")
                .status(Tender.Status.BIDDING)
                .purchaserName("测试采购方")
                .build();  // 无 projectManagerId
        TenderEvaluation evaluation = TenderEvaluation.builder()
                .tenderId(1L)
                .evaluatorId(18L)
                .build();

        when(tenderEvaluationRepository.findByTenderId(1L)).thenReturn(Optional.of(evaluation));
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));
        when(projectService.createProject(any(ProjectDTO.class))).thenReturn(ProjectDTO.builder()
                .id(101L).name("测试标讯").status(Project.Status.PENDING_INITIATION).build());
        when(bidTaskFactory.reuseOrCreate(any(), any(), any(), any())).thenReturn(TaskDTO.builder()
                .id(202L).title("【待立项】测试标讯").status(Task.Status.TODO).build());

        service.proceedToBid(1L, 99L);

        // 无标讯负责人时回退到评估人
        verify(projectService).createProject(argThat(project ->
                project.getManagerId().equals(18L)
        ));
    }
}
