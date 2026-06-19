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
                eventPublisher
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
        when(taskService.createTask(any(TaskDTO.class))).thenReturn(TaskDTO.builder()
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
        verify(taskService).createTask(argThat(task ->
                task.getProjectId().equals(101L)
                        && task.getStatus() == Task.Status.TODO
                        && task.getPriority() == Task.Priority.HIGH
        ));
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
        when(taskService.createTask(any(TaskDTO.class))).thenReturn(TaskDTO.builder()
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
        verify(taskService).createTask(argThat(task ->
                task.getProjectId().equals(101L)
                        && task.getAssigneeId().equals(99L)
                        && task.getStatus() == Task.Status.TODO
        ));
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
        verify(taskService, org.mockito.Mockito.never()).createTask(any());
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
}
