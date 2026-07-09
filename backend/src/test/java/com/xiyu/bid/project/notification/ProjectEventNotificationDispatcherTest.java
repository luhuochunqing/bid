// Output: ProjectEventNotificationDispatcher 事件通知标题、接收人、payload 与 Spec 030 过滤验证
// Pos: project/notification/ - 事件分发器测试
package com.xiyu.bid.project.notification;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.notification.service.ProjectNotificationRecipientPolicy.ProjectRole;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.notification.service.NotificationRecipientResolver;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectEventNotificationDispatcher — 项目事件通知分发")
class ProjectEventNotificationDispatcherTest {

    @Mock
    private NotificationApplicationService notificationService;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private NotificationRecipientResolver recipientResolver;

    private ProjectEventNotificationDispatcher dispatcher;

    private static final Long PID = 100L;
    private static final Long UID = 42L;

    @BeforeEach
    void setUp() {
        dispatcher = new ProjectEventNotificationDispatcher(
                notificationService, projectRepository, recipientResolver);
    }

    private Project project(String name) {
        Project p = new Project();
        p.setId(PID);
        p.setName(name);
        return p;
    }

    private Project project(String name, String customer) {
        Project p = project(name);
        p.setCustomer(customer);
        return p;
    }

    @Nested
    @DisplayName("notifyProjectArchived")
    class ProjectArchived {

        @Test
        @DisplayName("sends SYSTEM to BID_ADMIN/BID_TEAM_LEADER/BID_LEAD/BID_ASSISTANT with blueprint title/body")
        void sendsToProjectRoles() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目", "客户A")));
            Set<ProjectRole> expectedRoles = Set.of(
                    ProjectRole.BID_ADMIN,
                    ProjectRole.BID_TEAM_LEADER,
                    ProjectRole.BID_LEAD,
                    ProjectRole.BID_ASSISTANT);
            when(recipientResolver.resolveProjectRecipients(PID, expectedRoles, UID))
                    .thenReturn(List.of(1L, 2L, 3L));
            when(recipientResolver.filterByProjectAccess(List.of(1L, 2L, 3L), PID))
                    .thenReturn(List.of(1L, 2L));

            dispatcher.notifyProjectArchived(PID, "客户A", UID);

            ArgumentCaptor<CreateNotificationRequest> captor =
                    ArgumentCaptor.forClass(CreateNotificationRequest.class);
            verify(notificationService).createNotification(captor.capture(), eq(UID));
            CreateNotificationRequest req = captor.getValue();
            assertThat(req.type()).isEqualTo("SYSTEM");
            assertThat(req.sourceEntityType()).isEqualTo("PROJECT");
            assertThat(req.sourceEntityId()).isEqualTo(PID);
            assertThat(req.title()).isEqualTo("项目结项归档 - 测试项目");
            assertThat(req.body()).isEqualTo("【客户A - 测试项目】已结项归档，所有字段锁定，资料已自动归档");
            assertThat(req.recipientUserIds()).containsExactly(1L, 2L);
            assertThat(req.payload()).containsEntry("targetUrl", "/project/" + PID + "/closure");
            assertThat(req.payload()).containsEntry("projectName", "测试项目");
        }

        @Test
        @DisplayName("no candidates after filtering → no notification")
        void skipsWhenNoAccessibleRecipients() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            when(recipientResolver.resolveProjectRecipients(any(), any(), eq(UID)))
                    .thenReturn(List.of(1L));
            when(recipientResolver.filterByProjectAccess(any(), eq(PID)))
                    .thenReturn(List.of());

            dispatcher.notifyProjectArchived(PID, null, UID);

            verify(notificationService, never()).createNotification(any(), any());
        }
    }

    @Nested
    @DisplayName("notifyStageTransition")
    class StageTransition {

        @Test
        @DisplayName("sends SYSTEM to project roles + PROJECT_OWNER with stage display names")
        void sendsToProjectRolesAndOwner() {
            when(projectRepository.findById(PID))
                    .thenReturn(Optional.of(project("西安地铁", "西安地铁公司")));
            Set<ProjectRole> expectedRoles = Set.of(
                    ProjectRole.BID_ADMIN,
                    ProjectRole.BID_TEAM_LEADER,
                    ProjectRole.BID_LEAD,
                    ProjectRole.BID_ASSISTANT,
                    ProjectRole.PROJECT_OWNER);
            when(recipientResolver.resolveProjectRecipients(PID, expectedRoles, UID))
                    .thenReturn(List.of(1L, 2L));
            when(recipientResolver.filterByProjectAccess(List.of(1L, 2L), PID))
                    .thenReturn(List.of(1L, 2L));

            dispatcher.notifyStageTransition(PID, ProjectStage.DRAFTING, ProjectStage.EVALUATING, UID);

            ArgumentCaptor<CreateNotificationRequest> captor =
                    ArgumentCaptor.forClass(CreateNotificationRequest.class);
            verify(notificationService).createNotification(captor.capture(), eq(UID));
            CreateNotificationRequest req = captor.getValue();
            assertThat(req.type()).isEqualTo("SYSTEM");
            assertThat(req.title()).isEqualTo("阶段自动推进 - 西安地铁");
            assertThat(req.body()).contains("标书编制").contains("评标").contains("→");
            assertThat(req.recipientUserIds()).containsExactly(1L, 2L);
            assertThat(req.payload()).containsEntry("targetUrl", "/project/" + PID);
        }
    }

    @Nested
    @DisplayName("notifyTaskStatusChanged")
    class TaskStatusChanged {

        @Test
        @DisplayName("sends TASK_UPDATE to BID_LEAD/BID_ASSISTANT/TASK_EXECUTOR with blueprint title/body")
        void sendsToTaskRoles() {
            Long taskId = 99L;
            Long assigneeId = 8L;
            Long actorUserId = 7L;

            when(projectRepository.findById(PID))
                    .thenReturn(Optional.of(Project.builder().id(PID).name("西安地铁项目").build()));
            Set<ProjectRole> expectedRoles = Set.of(
                    ProjectRole.BID_LEAD,
                    ProjectRole.BID_ASSISTANT,
                    ProjectRole.TASK_EXECUTOR);
            when(recipientResolver.resolveProjectRecipients(PID, expectedRoles, actorUserId, assigneeId))
                    .thenReturn(List.of(1L, 2L, assigneeId));
            when(recipientResolver.filterByProjectAccess(List.of(1L, 2L, assigneeId), PID))
                    .thenReturn(List.of(1L, 2L, assigneeId));

            dispatcher.notifyTaskStatusChanged(
                    PID, taskId, "编写技术标书", "待处理", "审核中", assigneeId, actorUserId);

            ArgumentCaptor<CreateNotificationRequest> captor =
                    ArgumentCaptor.forClass(CreateNotificationRequest.class);
            verify(notificationService).createNotification(captor.capture(), eq(actorUserId));
            CreateNotificationRequest req = captor.getValue();
            assertThat(req.type()).isEqualTo("TASK_UPDATE");
            assertThat(req.title()).isEqualTo("任务状态变更 - 西安地铁项目 - 编写技术标书");
            assertThat(req.body()).isEqualTo("【西安地铁项目】任务「编写技术标书」状态发生变更：待处理 → 审核中");
            assertThat(req.recipientUserIds()).containsExactly(1L, 2L, assigneeId);
            assertThat(req.payload()).containsEntry("targetUrl", "/project/" + PID + "/drafting");
            assertThat(req.payload()).containsEntry("taskId", taskId);
            assertThat(req.payload()).containsEntry("taskName", "编写技术标书");
        }

        @Test
        @DisplayName("all filtered out → no notification")
        void skipsWhenAllFilteredOut() {
            when(projectRepository.findById(PID))
                    .thenReturn(Optional.of(Project.builder().id(PID).name("测试项目").build()));
            when(recipientResolver.resolveProjectRecipients(any(), any(), any(), any()))
                    .thenReturn(List.of(1L));
            when(recipientResolver.filterByProjectAccess(any(), eq(PID)))
                    .thenReturn(List.of());

            dispatcher.notifyTaskStatusChanged(
                    PID, 99L, "任务", "待处理", "审核中", 8L, 7L);

            verify(notificationService, never()).createNotification(any(), any());
        }
    }

    @Nested
    @DisplayName("notifyBidReviewSubmitted")
    class BidReviewSubmitted {

        @Test
        @DisplayName("sends BID_REVIEW to single reviewerId")
        void sendsToReviewer() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));

            dispatcher.notifyBidReviewSubmitted(PID, 77L, UID,
                    "测试标讯", "2026-07-01 10:00", "采购方", "提交人");

            ArgumentCaptor<CreateNotificationRequest> captor =
                    ArgumentCaptor.forClass(CreateNotificationRequest.class);
            verify(notificationService).createNotification(captor.capture(), eq(UID));
            CreateNotificationRequest req = captor.getValue();
            assertThat(req.type()).isEqualTo("BID_REVIEW");
            assertThat(req.recipientUserIds()).containsExactly(77L);
            assertThat(req.payload()).containsEntry("targetUrl", "/project/" + PID + "/drafting");
        }
    }
}
