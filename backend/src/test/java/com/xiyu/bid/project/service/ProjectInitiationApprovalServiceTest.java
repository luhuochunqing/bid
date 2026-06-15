// Input: ProjectInitiationApprovalService.approve 行为
// Output: Mockito 单元测试覆盖"审核通过后创建项目档案"+"幂等"两个场景
// Pos: backend test source - 蓝图 §4.1.1.1.1 修复回归
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.service;

import com.xiyu.bid.casework.application.ProjectArchiveWorkflowService;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.core.InitiationReviewStatus;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.core.ProjectStageTransitionPolicy;
import com.xiyu.bid.project.dto.InitiationApprovalRequest;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.entity.ProjectLeadAssignment;
import com.xiyu.bid.project.notification.ProjectNotificationHelper;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectInitiationApprovalServiceTest {

    @Mock private ProjectInitiationDetailsRepository initiationRepo;
    @Mock private ProjectLeadAssignmentRepository leadRepo;
    @Mock private ProjectStageService projectStageService;
    @Mock private ProjectAccessScopeService projectAccessScopeService;
    @Mock private UserRepository userRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectArchiveWorkflowService projectArchiveWorkflowService;
    @Mock private ProjectNotificationHelper notificationService;

    private ProjectInitiationApprovalService service;

    @BeforeEach
    void setUp() {
        service = new ProjectInitiationApprovalService(
                initiationRepo,
                leadRepo,
                projectStageService,
                projectAccessScopeService,
                userRepository,
                projectRepository,
                projectArchiveWorkflowService,
                notificationService);

        lenient().doNothing().when(projectAccessScopeService).assertCurrentUserCanAccessProject(100L);
        lenient().when(leadRepo.findByProjectId(100L))
                .thenReturn(Optional.empty());
        lenient().when(userRepository.findById(3L))
                .thenReturn(Optional.of(User.builder().id(3L).fullName("张三").build()));
        lenient().when(projectStageService.requestTransition(
                        eq(100L), eq(ProjectStage.DRAFTING),
                        any(ProjectStageTransitionPolicy.GateInputs.class)))
                .thenReturn(ProjectStage.DRAFTING);
    }

    @Test
    void approve_shouldCreateArchiveAfterStageTransition() {
        ProjectInitiationDetails details = ProjectInitiationDetails.builder()
                .id(1L)
                .projectId(100L)
                .reviewStatus(InitiationReviewStatus.PENDING_REVIEW.name())
                .locked(Boolean.FALSE)
                .build();
        when(initiationRepo.findByProjectId(100L)).thenReturn(Optional.of(details));
        when(projectStageService.currentStage(100L)).thenReturn(ProjectStage.INITIATED);
        when(initiationRepo.save(any(ProjectInitiationDetails.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(projectRepository.findById(100L))
                .thenReturn(Optional.of(Project.builder().id(100L).name("测试项目").build()));
        when(leadRepo.save(any(ProjectLeadAssignment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        InitiationApprovalRequest req = InitiationApprovalRequest.builder()
                .primaryLeadUserId(3L)
                .build();

        service.approve(100L, req, 5L);

        verify(projectArchiveWorkflowService, times(1))
                .createArchive(100L, "测试项目", "ACTIVE");
    }

    @Test
    void approve_shouldCreateArchiveExactlyOnce() {
        ProjectInitiationDetails details = ProjectInitiationDetails.builder()
                .id(1L)
                .projectId(100L)
                .reviewStatus(InitiationReviewStatus.PENDING_REVIEW.name())
                .locked(Boolean.FALSE)
                .build();
        when(initiationRepo.findByProjectId(100L)).thenReturn(Optional.of(details));
        when(projectStageService.currentStage(100L)).thenReturn(ProjectStage.INITIATED);
        when(initiationRepo.save(any(ProjectInitiationDetails.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(projectRepository.findById(100L))
                .thenReturn(Optional.of(Project.builder().id(100L).name("测试项目").build()));
        when(leadRepo.save(any(ProjectLeadAssignment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        InitiationApprovalRequest req = InitiationApprovalRequest.builder()
                .primaryLeadUserId(3L)
                .build();

        service.approve(100L, req, 5L);

        assertThatThrownBy(() -> service.approve(100L, req, 5L))
                .isInstanceOf(ResponseStatusException.class);

        verify(projectArchiveWorkflowService, times(1))
                .createArchive(100L, "测试项目", "ACTIVE");
    }
}
