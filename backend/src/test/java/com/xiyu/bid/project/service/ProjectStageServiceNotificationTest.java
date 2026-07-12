// Output: ProjectStageService triggers pending-closure notification on RETROSPECTIVE -> CLOSED
// Pos: backend test source / project service notification trigger unit test
package com.xiyu.bid.project.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.notification.application.ProjectClosureNotificationApplicationService;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.core.ProjectStageTransitionPolicy;
import com.xiyu.bid.project.notification.ProjectNotificationService;
import com.xiyu.bid.project.repository.ProjectClosureRepository;
import com.xiyu.bid.project.repository.ProjectResultRepository;
import com.xiyu.bid.project.repository.ProjectRetrospectiveRepository;
import com.xiyu.bid.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ProjectStageService — RETROSPECTIVE→CLOSED 触发待结项申请通知")
class ProjectStageServiceNotificationTest {

    private static final Long PID = 1L;
    private static final Long TRIGGERED_BY = 7L;
    private static final String PROJECT_NAME = "西域智能投标项目";
    private static final ProjectStageTransitionPolicy.GateInputs GATE =
            ProjectStageTransitionPolicy.GateInputs.EMPTY;

    private ProjectRepository projectRepo;
    private ApplicationEventPublisher eventPublisher;
    private ProjectNotificationService notificationService;
    private ProjectResultRepository projectResultRepository;
    private ProjectClosureRepository closureRepository;
    private ProjectRetrospectiveRepository retrospectiveRepository;
    private ProjectClosureNotificationApplicationService closureNotificationService;
    private ProjectStageService service;

    @BeforeEach
    void setUp() {
        projectRepo = mock(ProjectRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        notificationService = mock(ProjectNotificationService.class);
        projectResultRepository = mock(ProjectResultRepository.class);
        closureRepository = mock(ProjectClosureRepository.class);
        retrospectiveRepository = mock(ProjectRetrospectiveRepository.class);
        closureNotificationService = mock(ProjectClosureNotificationApplicationService.class);
        service = new ProjectStageService(
                projectRepo, eventPublisher, notificationService,
                projectResultRepository, closureRepository, retrospectiveRepository,
                closureNotificationService);
        when(projectRepo.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));
        when(projectResultRepository.findByProjectId(PID)).thenReturn(Optional.empty());
    }

    private void mockProjectAtStage(ProjectStage stage) {
        Project p = new Project();
        p.setId(PID);
        p.setName(PROJECT_NAME);
        p.setStage(stage.name());
        when(projectRepo.findById(PID)).thenReturn(Optional.of(p));
    }

    @Test
    @DisplayName("RETROSPECTIVE → CLOSED 成功后触发待结项申请通知")
    void retrospectiveToClosed_triggersPendingClosureNotification() {
        mockProjectAtStage(ProjectStage.RETROSPECTIVE);

        ProjectStage result = service.requestTransition(
                PID, ProjectStage.CLOSED, GATE, null, TRIGGERED_BY);

        assertEquals(ProjectStage.CLOSED, result);
        verify(closureNotificationService).sendPendingClosureApplicationNotification(
                PID, PROJECT_NAME, TRIGGERED_BY);
    }

    @Test
    @DisplayName("非复盘阶段推进至 CLOSED 不触发待结项申请通知")
    void resultPendingToClosed_doesNotTriggerPendingClosureNotification() {
        mockProjectAtStage(ProjectStage.RESULT_PENDING);

        service.requestTransition(PID, ProjectStage.CLOSED, GATE, null, TRIGGERED_BY);

        verify(closureNotificationService, never()).sendPendingClosureApplicationNotification(
                any(), any(), any());
    }

    @Test
    @DisplayName("RETROSPECTIVE → 其他阶段不触发待结项申请通知")
    void retrospectiveToOtherStage_doesNotTriggerPendingClosureNotification() {
        // RETROSPECTIVE 只允许到 CLOSED，这里用异常路径验证条件判断
        mockProjectAtStage(ProjectStage.RETROSPECTIVE);

        try {
            service.requestTransition(PID, ProjectStage.RESULT_PENDING, GATE, null, TRIGGERED_BY);
        } catch (Exception ignored) {
            // expected: illegal transition
        }

        verify(closureNotificationService, never()).sendPendingClosureApplicationNotification(
                any(), any(), any());
    }

    @Test
    @DisplayName("待结项通知服务异常不阻塞阶段转换")
    void closureNotificationFailure_doesNotBlockTransition() {
        mockProjectAtStage(ProjectStage.RETROSPECTIVE);
        doThrow(new RuntimeException("通知服务故障"))
                .when(closureNotificationService)
                .sendPendingClosureApplicationNotification(PID, PROJECT_NAME, TRIGGERED_BY);

        ProjectStage result = service.requestTransition(
                PID, ProjectStage.CLOSED, GATE, null, TRIGGERED_BY);

        assertEquals(ProjectStage.CLOSED, result);
        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepo).save(captor.capture());
        assertThat(captor.getValue().getStage()).isEqualTo(ProjectStage.CLOSED.name());
    }

    @Test
    @DisplayName("旧重载方法（无 triggeredByUserId）在 RETROSPECTIVE → CLOSED 时仍触发通知")
    void legacyOverload_retrospectiveToClosed_triggersNotificationWithNullUser() {
        mockProjectAtStage(ProjectStage.RETROSPECTIVE);

        service.requestTransition(PID, ProjectStage.CLOSED, GATE);

        verify(closureNotificationService).sendPendingClosureApplicationNotification(
                PID, PROJECT_NAME, null);
    }
}
