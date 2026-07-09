// Output: 13 个 public 通知方法的分支覆盖
// Pos: project/notification/ - 纯编排层测试
package com.xiyu.bid.project.notification;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.matrixcollaboration.entity.ProjectMember;
import com.xiyu.bid.matrixcollaboration.repository.ProjectMemberRepository;
import com.xiyu.bid.notification.service.NotificationRecipientResolver;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.entity.ProjectLeadAssignment;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.security.EffectiveRoleResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectNotificationService — 13 个通知方法")
class ProjectNotificationServiceTest {

    @Mock
    private NotificationApplicationService notificationService;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private ProjectLeadAssignmentRepository leadAssignmentRepository;
    @Mock
    private EffectiveRoleResolver effectiveRoleResolver;
    @Mock
    private NotificationRecipientResolver recipientResolver;

    @Captor
    private ArgumentCaptor<CreateNotificationRequest> requestCaptor;

    private ProjectNotificationService svc;

    private static final Long PID = 100L;
    private static final Long UID = 42L;
    private static final Long MANAGER_ID = 88L;
    private static final Long TASK_ID = 77L;
    private static final Long ASSIGNEE_ID = 55L;
    private static final Long SYSTEM_USER_ID = 0L;

    @BeforeEach
    void setUp() {
        // 使用真实的 ProjectEventNotificationDispatcher（非 mock），让 StageTransition/BidReviewSubmitted
        // 等委托方法的测试仍能验证最终 notificationService 的调用。
        ProjectEventNotificationDispatcher eventDispatcher = new ProjectEventNotificationDispatcher(
                notificationService, projectRepository, recipientResolver);
        svc = new ProjectNotificationService(notificationService, projectRepository,
                userRepository, projectMemberRepository, leadAssignmentRepository, effectiveRoleResolver,
                recipientResolver, eventDispatcher);
        // 默认 stubbing：resolver 返回空列表，避免 UnnecessaryStubbingException；
        // 各测试按需 override
        lenient().when(recipientResolver.getAdminUserIds()).thenReturn(List.of());
        lenient().when(recipientResolver.getProjectMemberUserIds(any(), any()))
                .thenReturn(List.of());
    }

    private Project project(String name) {
        Project p = new Project();
        p.setId(PID);
        p.setName(name);
        p.setManagerId(MANAGER_ID);
        return p;
    }

    private User user(Long id, String fullName) {
        User u = new User();
        u.setId(id);
        u.setFullName(fullName);
        return u;
    }

    private User userWithRole(String roleCode) {
        User u = new User();
        u.setId(ASSIGNEE_ID);
        u.setUsername("assignee");
        u.setPassword("dummy");
        u.setEmail("assignee@test.local");
        u.setFullName("被分配人");
        u.setRole(User.Role.ADMIN);
        RoleProfile profile = RoleProfile.builder().code(roleCode).name(roleCode).build();
        u.setRoleProfile(profile);
        return u;
    }

    private ProjectMember member(Long userId, String permissionLevel) {
        ProjectMember m = new ProjectMember();
        m.setUserId(userId);
        m.setPermissionLevel(permissionLevel);
        return m;
    }

    @Nested
    @DisplayName("notifyInitiationSubmitted")
    class InitiationSubmitted {

        @Test
        @DisplayName("sends APPROVAL to admins via sendToAdmins")
        void sendsToAdmins() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            when(recipientResolver.getAdminUserIds()).thenReturn(List.of(1L, 2L));

            svc.notifyInitiationSubmitted(PID, UID);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            assertThat(requestCaptor.getValue().type()).isEqualTo("APPROVAL");
            assertThat(requestCaptor.getValue().recipientUserIds()).containsExactlyInAnyOrder(1L, 2L);
        }

        @Test
        @DisplayName("sourceEntityType must be uppercase PROJECT (fix for CO-439 notification jump failure)")
        void sourceEntityType_IsUppercasePROJECT() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            when(recipientResolver.getAdminUserIds()).thenReturn(List.of(1L));

            svc.notifyInitiationSubmitted(PID, UID);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            assertThat(requestCaptor.getValue().sourceEntityType()).isEqualTo("PROJECT");
        }

        @Test
        @DisplayName("project not found → skipped in sendNotification → no createNotification")
        void skipsWhenProjectNotFound() {
            svc.notifyInitiationSubmitted(PID, UID);

            verify(notificationService, never()).createNotification(any(), any());
        }
    }

    @Nested
    @DisplayName("notifyInitiationApproved")
    class InitiationApproved {

        @Test
        @DisplayName("sends INFO to manager + leads")
        void sendsToManagerAndLeads() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            when(projectMemberRepository.findByProjectId(PID))
                    .thenReturn(List.of(member(MANAGER_ID, "LEAD"), member(99L, "ADMIN"), member(77L, "VIEWER")));
            when(leadAssignmentRepository.findByProjectId(PID)).thenReturn(Optional.empty());

            svc.notifyInitiationApproved(PID, UID);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            assertThat(requestCaptor.getValue().type()).isEqualTo("INFO");
            assertThat(requestCaptor.getValue().recipientUserIds()).containsExactlyInAnyOrder(88L, 99L);
        }

        @Test
        @DisplayName("sends INFO to manager + primary/secondary leads from lead assignment")
        void sendsToManagerAndLeadsFromLeadAssignment() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            when(projectMemberRepository.findByProjectId(PID)).thenReturn(List.of());
            when(leadAssignmentRepository.findByProjectId(PID))
                    .thenReturn(Optional.of(ProjectLeadAssignment.builder()
                            .projectId(PID)
                            .primaryLeadUserId(200L)
                            .secondaryLeadUserId(201L)
                            .build()));

            svc.notifyInitiationApproved(PID, UID);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            assertThat(requestCaptor.getValue().type()).isEqualTo("INFO");
            assertThat(requestCaptor.getValue().recipientUserIds())
                    .containsExactlyInAnyOrder(MANAGER_ID, 200L, 201L);
        }

        @Test
        @DisplayName("project not found → no notification")
        void skipsWhenProjectNotFound() {
            when(projectRepository.findById(PID)).thenReturn(Optional.empty());

            svc.notifyInitiationApproved(PID, UID);

            verify(notificationService, never()).createNotification(any(), any());
        }
    }

    @Nested
    @DisplayName("notifyInitiationRejected")
    class InitiationRejected {

        @Test
        @DisplayName("sends INFO to manager")
        void sendsToManager() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));

            svc.notifyInitiationRejected(PID, UID, "缺少预算");

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            assertThat(requestCaptor.getValue().type()).isEqualTo("INFO");
            assertThat(requestCaptor.getValue().recipientUserIds()).containsExactly(MANAGER_ID);
        }

        @Test
        @DisplayName("project not found → no notification")
        void skipsWhenProjectNotFound() {
            when(projectRepository.findById(PID)).thenReturn(Optional.empty());

            svc.notifyInitiationRejected(PID, UID, "缺资料");

            verify(notificationService, never()).createNotification(any(), any());
        }
    }

    @Nested
    @DisplayName("notifyStageTransition")
    class StageTransition {

        @Test
        @DisplayName("sends INFO to all team members when project found with members")
        void sendsToAllTeamMembers() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            when(recipientResolver.getProjectMemberUserIds(PID, null))
                    .thenReturn(List.of(1L, 2L));

            svc.notifyStageTransition(PID, ProjectStage.DRAFTING, ProjectStage.EVALUATING, UID);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            assertThat(requestCaptor.getValue().recipientUserIds()).containsExactlyInAnyOrder(1L, 2L);
        }

        @Test
        @DisplayName("legacy signature uses system actor instead of null createdBy")
        void legacySignatureUsesSystemActor() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            when(recipientResolver.getProjectMemberUserIds(PID, null))
                    .thenReturn(List.of(1L, 2L));

            svc.notifyStageTransition(PID, ProjectStage.DRAFTING, ProjectStage.EVALUATING);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(0L));
            assertThat(requestCaptor.getValue().recipientUserIds()).containsExactlyInAnyOrder(1L, 2L);
        }

        @Test
        @DisplayName("no team members → no notification")
        void skipsWhenNoTeamMembers() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            // resolver 默认返回空列表（setUp lenient），无需额外 stub

            svc.notifyStageTransition(PID, ProjectStage.DRAFTING, ProjectStage.EVALUATING);

            verify(notificationService, never()).createNotification(any(), any());
        }

        @Test
        @DisplayName("project not found → no notification")
        void skipsWhenProjectNotFound() {
            when(projectRepository.findById(PID)).thenReturn(Optional.empty());

            svc.notifyStageTransition(PID, ProjectStage.DRAFTING, ProjectStage.EVALUATING);

            verify(notificationService, never()).createNotification(any(), any());
        }
    }

    @Nested
    @DisplayName("notifyTaskAssigned")
    class TaskAssigned {

        @Test
        @DisplayName("non-bid-otherDept 角色 → targetUrl 指向 /project/{id}/drafting")
        void sendsToAssigneeWithProjectDraftingUrl() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            when(userRepository.findById(ASSIGNEE_ID)).thenReturn(Optional.of(userWithRole("bid-Team")));
            when(effectiveRoleResolver.resolveRoleCode(any(User.class))).thenReturn("bid-Team");

            svc.notifyTaskAssigned(PID, TASK_ID, "任务标题", ASSIGNEE_ID, UID);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            CreateNotificationRequest req = requestCaptor.getValue();
            assertThat(req.recipientUserIds()).containsExactly(ASSIGNEE_ID);
            assertThat(req.title()).isEqualTo("任务分配 - 测试项目 - 任务标题");
            assertThat(req.payload()).containsEntry("targetUrl", "/project/" + PID + "/drafting");
            assertThat(req.payload()).containsEntry("taskId", String.valueOf(TASK_ID));
        }

        @Test
        @DisplayName("bid-otherDept 角色 → targetUrl 指向 /task-board（CO-474 根因修复）")
        void sendsToBidOtherDeptWithTaskBoardUrl() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            when(userRepository.findById(ASSIGNEE_ID)).thenReturn(Optional.of(userWithRole(RoleProfileCatalog.BID_OTHER_DEPT_CODE)));
            when(effectiveRoleResolver.resolveRoleCode(any(User.class))).thenReturn(RoleProfileCatalog.BID_OTHER_DEPT_CODE);

            svc.notifyTaskAssigned(PID, TASK_ID, "任务标题", ASSIGNEE_ID, UID);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            CreateNotificationRequest req = requestCaptor.getValue();
            assertThat(req.recipientUserIds()).containsExactly(ASSIGNEE_ID);
            assertThat(req.title()).isEqualTo("任务分配 - 测试项目 - 任务标题");
            assertThat(req.payload()).containsEntry("targetUrl", "/task-board?taskId=" + TASK_ID + "&projectId=" + PID);
            assertThat(req.payload()).containsEntry("taskId", String.valueOf(TASK_ID));
        }

        @Test
        @DisplayName("project not found → skip in sendNotification")
        void skipsWhenProjectNotFound() {
            when(userRepository.findById(ASSIGNEE_ID)).thenReturn(Optional.of(userWithRole("bid-Team")));
            when(effectiveRoleResolver.resolveRoleCode(any(User.class))).thenReturn("bid-Team");
            when(projectRepository.findById(PID)).thenReturn(Optional.empty());

            svc.notifyTaskAssigned(PID, TASK_ID, "任务标题", ASSIGNEE_ID, UID);

            verify(notificationService, never()).createNotification(any(), any());
        }
    }

    @Nested
    @DisplayName("notifyBidReviewResult")
    class BidReviewResult {

        @Test
        @DisplayName("approved → sends INFO to recipient")
        void approvedSendsToRecipient() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));

            svc.notifyBidReviewResult(PID, 77L, true, UID);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            assertThat(requestCaptor.getValue().title()).contains("通过");
        }

        @Test
        @DisplayName("rejected → sends INFO to recipient with 驳回 label")
        void rejectedSendsToRecipient() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));

            svc.notifyBidReviewResult(PID, 77L, false, UID);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            assertThat(requestCaptor.getValue().title()).contains("驳回");
        }

        @Test
        @DisplayName("null recipientId → skipped")
        void skipsWhenRecipientIsNull() {
            svc.notifyBidReviewResult(PID, null, true, UID);

            verify(notificationService, never()).createNotification(any(), any());
        }
    }

    @Nested
    @DisplayName("notifyBidReviewSubmitted")
    class BidReviewSubmitted {

        @Test
        @DisplayName("sends BID_REVIEW with PROJECT sourceEntityType and targetUrl in payload")
        void sendsBidReviewNotification() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));

            svc.notifyBidReviewSubmitted(PID, 77L, UID,
                    "测试标讯", "2026-07-01 10:00", "采购方", "提交人");

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            CreateNotificationRequest req = requestCaptor.getValue();
            assertThat(req.type()).isEqualTo("BID_REVIEW");
            assertThat(req.sourceEntityType()).isEqualTo("PROJECT");
            assertThat(req.sourceEntityId()).isEqualTo(PID);
            assertThat(req.recipientUserIds()).containsExactly(77L);
            assertThat(req.payload()).isNotNull();
            assertThat(req.payload()).containsKey("targetUrl");
            assertThat(req.payload().get("targetUrl")).isEqualTo("/project/100/drafting");
        }

        @Test
        @DisplayName("null reviewerId → skipped")
        void skipsWhenReviewerIsNull() {
            svc.notifyBidReviewSubmitted(PID, null, UID,
                    "测试标讯", "2026-07-01 10:00", "采购方", "提交人");

            verify(notificationService, never()).createNotification(any(), any());
        }

        @Test
        @DisplayName("project not found → skipped")
        void skipsWhenProjectNotFound() {
            when(projectRepository.findById(PID)).thenReturn(Optional.empty());

            svc.notifyBidReviewSubmitted(PID, 77L, UID,
                    "测试标讯", "2026-07-01 10:00", "采购方", "提交人");

            verify(notificationService, never()).createNotification(any(), any());
        }

        @Test
        @DisplayName("handles null tender fields gracefully")
        void handlesNullTenderFields() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));

            svc.notifyBidReviewSubmitted(PID, 77L, UID,
                    null, null, null, null);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            CreateNotificationRequest req = requestCaptor.getValue();
            assertThat(req.body()).contains("项目名称");
        }
    }

    @Nested
    @DisplayName("notifyEvaluationSubStage")
    class EvaluationSubStage {

        @Test
        @DisplayName("team members exist → sends INFO")
        void sendsToTeamMembers() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            when(recipientResolver.getProjectMemberUserIds(PID, null)).thenReturn(List.of(1L));

            svc.notifyEvaluationSubStage(PID, "技术评审", UID);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            assertThat(requestCaptor.getValue().recipientUserIds()).containsExactly(1L);
        }

        @Test
        @DisplayName("no team members → no notification")
        void skipsWhenNoTeamMembers() {

            svc.notifyEvaluationSubStage(PID, "技术评审", UID);
            verify(notificationService, never()).createNotification(any(), any());
        }
    }

    @Nested
    @DisplayName("notifyAbandonBid")
    class AbandonBid {

        @Test
        @DisplayName("sends INFO to team + admins")
        void sendsToTeamAndAdmins() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            when(recipientResolver.getProjectMemberUserIds(PID, null)).thenReturn(List.of(1L));
            when(recipientResolver.getAdminUserIds()).thenReturn(List.of(2L));

            svc.notifyAbandonBid(PID, UID);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            assertThat(requestCaptor.getValue().recipientUserIds()).containsExactlyInAnyOrder(1L, 2L);
        }

        @Test
        @DisplayName("all empty → no notification")
        void skipsWhenAllEmpty() {
            // resolver 默认返回空列表（setUp lenient）
            when(recipientResolver.getAdminUserIds()).thenReturn(List.of());

            svc.notifyAbandonBid(PID, UID);

            verify(notificationService, never()).createNotification(any(), any());
        }
    }

    @Nested
    @DisplayName("notifyResultRegistered")
    class ResultRegistered {

        @Test
        @DisplayName("sends INFO to team + admins")
        void sendsToTeamAndAdmins() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            when(recipientResolver.getProjectMemberUserIds(PID, null)).thenReturn(List.of(1L));
            when(recipientResolver.getAdminUserIds()).thenReturn(List.of(2L));

            svc.notifyResultRegistered(PID, "中标", UID);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            assertThat(requestCaptor.getValue().recipientUserIds()).containsExactlyInAnyOrder(1L, 2L);
        }
    }

    @Nested
    @DisplayName("notifyRetrospectiveSubmitted")
    class RetrospectiveSubmitted {

        @Test
        @DisplayName("sends APPROVAL to admins")
        void sendsToAdmins() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            when(recipientResolver.getAdminUserIds()).thenReturn(List.of(1L));

            svc.notifyRetrospectiveSubmitted(PID, UID);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            assertThat(requestCaptor.getValue().type()).isEqualTo("APPROVAL");
            assertThat(requestCaptor.getValue().recipientUserIds()).containsExactly(1L);
        }
    }

    @Nested
    @DisplayName("notifyRetrospectiveReviewed")
    class RetrospectiveReviewed {

        @Test
        @DisplayName("approved → sends INFO to submitter")
        void approvedSendsToSubmitter() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));

            svc.notifyRetrospectiveReviewed(PID, 77L, true, UID);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            assertThat(requestCaptor.getValue().title()).contains("通过");
            assertThat(requestCaptor.getValue().recipientUserIds()).containsExactly(77L);
        }

        @Test
        @DisplayName("rejected → sends INFO to submitter with 驳回 label")
        void rejectedSendsToSubmitter() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));

            svc.notifyRetrospectiveReviewed(PID, 77L, false, UID);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            assertThat(requestCaptor.getValue().title()).contains("驳回");
        }

        @Test
        @DisplayName("null submitterId → skipped")
        void skipsWhenSubmitterIsNull() {
            svc.notifyRetrospectiveReviewed(PID, null, true, UID);

            verify(notificationService, never()).createNotification(any(), any());
        }
    }

    @Nested
    @DisplayName("notifyClosureSubmitted")
    class ClosureSubmitted {

        @Test
        @DisplayName("sends APPROVAL to admins")
        void sendsToAdmins() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            when(recipientResolver.getAdminUserIds()).thenReturn(List.of(1L));

            svc.notifyClosureSubmitted(PID, UID);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            assertThat(requestCaptor.getValue().type()).isEqualTo("APPROVAL");
            assertThat(requestCaptor.getValue().recipientUserIds()).containsExactly(1L);
        }
    }

    @Nested
    @DisplayName("notifyClosureReviewed")
    class ClosureReviewed {

        @Test
        @DisplayName("approved → sends INFO to submitter")
        void approvedSendsToSubmitter() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));

            svc.notifyClosureReviewed(PID, 77L, true, UID);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            assertThat(requestCaptor.getValue().title()).contains("通过");
            assertThat(requestCaptor.getValue().recipientUserIds()).containsExactly(77L);
        }

        @Test
        @DisplayName("rejected → sends INFO to submitter with 驳回 label")
        void rejectedSendsToSubmitter() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));

            svc.notifyClosureReviewed(PID, 77L, false, UID);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            assertThat(requestCaptor.getValue().title()).contains("驳回");
        }

        @Test
        @DisplayName("null submitterId → skipped")
        void skipsWhenSubmitterIsNull() {
            svc.notifyClosureReviewed(PID, null, true, UID);

            verify(notificationService, never()).createNotification(any(), any());
        }
    }
}
