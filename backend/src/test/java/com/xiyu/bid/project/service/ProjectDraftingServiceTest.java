// Input: ProjectDraftingService 行为
// Output: Mockito 单元测试覆盖 assignLeads + gateAdvanceToEvaluation (happy + deny)
// Pos: backend test source
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.dto.ProjectLeadAssignmentRequest;
import com.xiyu.bid.project.entity.ProjectLeadAssignment;
import com.xiyu.bid.project.notification.ProjectNotificationHelper;
import com.xiyu.bid.project.repository.ProjectEvaluationRepository;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

@ExtendWith(MockitoExtension.class)
class ProjectDraftingServiceTest {

    @Mock ProjectLeadAssignmentRepository leadRepo;
    @Mock ProjectRepository projectRepository;
    @Mock TaskRepository taskRepository;
    @Mock ProjectStageService projectStageService;
    @Mock com.xiyu.bid.service.ProjectAccessScopeService projectAccessScopeService;
    @Mock BidReviewAppService bidReviewAppService;
    @Mock UserRepository userRepository;
    @Mock ProjectEvaluationRepository projectEvaluationRepository;
    @Mock ProjectNotificationHelper notificationService;

    ProjectDraftingService service;

    @BeforeEach
    void setUp() {
        service = new ProjectDraftingService(leadRepo, projectRepository, taskRepository, projectStageService, projectAccessScopeService, userRepository, bidReviewAppService, projectEvaluationRepository, notificationService);
        lenient().when(projectRepository.findById(1L))
                .thenReturn(Optional.of(Project.builder().id(1L).build()));
        lenient().when(leadRepo.save(any(ProjectLeadAssignment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(projectStageService.currentStage(1L)).thenReturn(ProjectStage.DRAFTING);
        lenient().when(bidReviewAppService.getReviewState(1L))
                .thenReturn(new BidReviewAppService.ReviewState("DRAFT", null, null, null));
    }

    @Test
    void assignLeads_happy_createsAndReturns() {
        when(leadRepo.findByProjectId(1L)).thenReturn(Optional.empty());
        when(taskRepository.findByProjectId(1L)).thenReturn(List.of());
        var view = service.assignLeads(1L,
                ProjectLeadAssignmentRequest.builder()
                        .primaryLeadUserId(10L).secondaryLeadUserId(20L).build(),
                99L);
        assertThat(view.getPrimaryLeadUserId()).isEqualTo(10L);
        assertThat(view.getSecondaryLeadUserId()).isEqualTo(20L);
        assertThat(view.getGateReady()).isTrue();
    }

    @Test
    void assignLeads_missingPrimary_422() {
        assertThatThrownBy(() -> service.assignLeads(1L,
                ProjectLeadAssignmentRequest.builder().secondaryLeadUserId(20L).build(), 99L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void assignLeads_samePrimaryAndSecondary_422() {
        assertThatThrownBy(() -> service.assignLeads(1L,
                ProjectLeadAssignmentRequest.builder()
                        .primaryLeadUserId(10L).secondaryLeadUserId(10L).build(), 99L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void gate_allowsWhenNoTasks() {
        when(taskRepository.findByProjectId(1L)).thenReturn(List.of());
        when(leadRepo.findByProjectId(1L)).thenReturn(Optional.empty());
        var view = service.gateAdvanceToEvaluation(1L, 99L);
        assertThat(view.getGateReady()).isTrue();
        assertThat(view.getIncompleteTaskCount()).isZero();
    }

    @Test
    void gate_deniesWhenIncomplete_409() {
        when(taskRepository.findByProjectId(1L)).thenReturn(List.of(
                Task.builder().id(1L).projectId(1L).title("a").status(Task.Status.COMPLETED).build(),
                Task.builder().id(2L).projectId(1L).title("b").status(Task.Status.IN_PROGRESS).build()));
        assertThatThrownBy(() -> service.gateAdvanceToEvaluation(1L, 99L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void gate_ignoresCancelled() {
        when(taskRepository.findByProjectId(1L)).thenReturn(List.of(
                Task.builder().id(1L).projectId(1L).title("a").status(Task.Status.COMPLETED).build(),
                Task.builder().id(2L).projectId(1L).title("b").status(Task.Status.CANCELLED).build()));
        when(leadRepo.findByProjectId(1L)).thenReturn(Optional.empty());
        var view = service.gateAdvanceToEvaluation(1L, 99L);
        assertThat(view.getGateReady()).isTrue();
    }

    @Test
    void assignLeads_atClosedStage_throws423() {
        when(projectStageService.currentStage(1L)).thenReturn(ProjectStage.CLOSED);
        assertThatThrownBy(() -> service.assignLeads(1L,
                ProjectLeadAssignmentRequest.builder().primaryLeadUserId(10L).build(), 99L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.LOCKED);
    }

    @Test
    void get_returnsViewWithLeadsAndGate() {
        when(leadRepo.findByProjectId(1L)).thenReturn(Optional.of(
                ProjectLeadAssignment.builder().id(5L).projectId(1L)
                        .primaryLeadUserId(10L).secondaryLeadUserId(20L).build()));
        when(taskRepository.findByProjectId(1L)).thenReturn(List.of(
                Task.builder().id(1L).projectId(1L).title("a").status(Task.Status.TODO).build()));
        var view = service.get(1L);
        assertThat(view.getPrimaryLeadUserId()).isEqualTo(10L);
        assertThat(view.getGateReady()).isFalse();
        assertThat(view.getIncompleteTaskCount()).isEqualTo(1);
    }

    // ── submitBid 角色校验 ────────────────────────────────────────────────

    private static com.xiyu.bid.entity.RoleProfile roleProfile(String code) {
        return com.xiyu.bid.entity.RoleProfile.builder().code(code).build();
    }

    private com.xiyu.bid.entity.User mockUser(Long id, String roleProfileCode) {
        return com.xiyu.bid.entity.User.builder()
                .id(id)
                .username("test-" + id)
                .role(com.xiyu.bid.entity.User.Role.MANAGER)
                .roleProfile(roleProfileCode != null ? roleProfile(roleProfileCode) : null)
                .build();
    }

    private void prepareSubmitBidHappyPath() {
        lenient().when(taskRepository.findByProjectId(1L)).thenReturn(List.of());
        lenient().when(leadRepo.findByProjectId(1L)).thenReturn(Optional.empty());
        lenient().when(projectStageService.currentStage(1L)).thenReturn(ProjectStage.DRAFTING);
        lenient().when(bidReviewAppService.getReviewState(1L))
                .thenReturn(new BidReviewAppService.ReviewState("APPROVED", null, null, null));
        lenient().when(projectEvaluationRepository.findByProjectId(1L)).thenReturn(Optional.empty());
        lenient().when(projectEvaluationRepository.save(any(com.xiyu.bid.project.entity.ProjectEvaluation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void submitBid_sales_allowed() {
        prepareSubmitBidHappyPath();
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser(1L, "sales")));
        var view = service.submitBid(1L, 1L);
        assertThat(view).isNotNull();
    }

    @Test
    void submitBid_initializesEvaluationRecord() {
        prepareSubmitBidHappyPath();
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser(1L, "sales")));
        service.submitBid(1L, 1L);
        verify(projectEvaluationRepository).save(argThat(e ->
                e.getProjectId().equals(1L)
                        && "IN_PROGRESS".equals(e.getSubStage())
                        && e.getEvaluationStartedAt() != null
                        && "".equals(e.getNotes())));
    }

    @Test
    void submitBid_bidAdmin_allowed() {
        prepareSubmitBidHappyPath();
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser(1L, "bid_admin")));
        var view = service.submitBid(1L, 1L);
        assertThat(view).isNotNull();
    }

    @Test
    void submitBid_bidLead_allowed() {
        prepareSubmitBidHappyPath();
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser(1L, "bid_lead")));
        var view = service.submitBid(1L, 1L);
        assertThat(view).isNotNull();
    }

    @Test
    void submitBid_auditor_denied_403() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser(1L, "auditor")));
        assertThatThrownBy(() -> service.submitBid(1L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void submitBid_taskExecutor_denied_403() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser(1L, "task_executor")));
        assertThatThrownBy(() -> service.submitBid(1L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void submitBid_noRoleProfile_denied_403() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser(1L, null)));
        assertThatThrownBy(() -> service.submitBid(1L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.FORBIDDEN);
    }
}
