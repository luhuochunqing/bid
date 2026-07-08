package com.xiyu.bid.project.notification;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.notification.service.NotificationRecipientResolver;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.repository.ProjectRepository;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TaskReviewNotificationService — 任务审核通知")
class TaskReviewNotificationServiceTest {

    @Mock
    private NotificationApplicationService notificationService;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EffectiveRoleResolver effectiveRoleResolver;
    @Mock
    private NotificationRecipientResolver recipientResolver;

    @Captor
    private ArgumentCaptor<CreateNotificationRequest> requestCaptor;

    private TaskReviewNotificationService svc;

    private static final Long PID = 100L;
    private static final Long TASK_ID = 77L;
    private static final Long UID = 42L;
    private static final Long ASSIGNEE_ID = 55L;
    private static final Long SYSTEM_USER_ID = 0L;

    @BeforeEach
    void setUp() {
        svc = new TaskReviewNotificationService(notificationService, projectRepository,
                userRepository, effectiveRoleResolver, recipientResolver);
        // 默认 filterByProjectAccess 透传候选集合（既有用例语义保持不变）；
        // 新增的过滤测试用例会 override 此 stub。
        lenient().when(recipientResolver.filterByProjectAccess(any(), eq(PID)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private Project project(String name) {
        Project p = new Project();
        p.setId(PID);
        p.setName(name);
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

    @Nested
    @DisplayName("notifyTaskReviewSubmitted")
    class TaskReviewSubmitted {

        @Test
        @DisplayName("sends TASK_UPDATE to reviewers excluding submitter")
        void sendsToReviewersExcludingSubmitter() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            when(userRepository.findEnabledByRoleProfileCodes(RoleProfileCatalog.TASK_MUTATION_ALLOWED_ROLES))
                    .thenReturn(List.of(user(1L, "审核人A"), user(2L, "审核人B"), user(UID, "提交人")));

            svc.notifyTaskReviewSubmitted(PID, TASK_ID, "任务标题", "提交人", UID);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            CreateNotificationRequest req = requestCaptor.getValue();
            assertThat(req.type()).isEqualTo("TASK_UPDATE");
            assertThat(req.sourceEntityType()).isEqualTo("PROJECT");
            assertThat(req.recipientUserIds()).containsExactlyInAnyOrder(1L, 2L);
            assertThat(req.title()).isEqualTo("任务审核通知 - 测试项目 - 任务标题");
            assertThat(req.body()).contains("任务：任务标题").contains("提交人：提交人");
            assertThat(req.payload()).containsEntry("targetUrl", "/project/" + PID + "/drafting");
            assertThat(req.payload()).containsEntry("taskId", String.valueOf(TASK_ID));
        }

        @Test
        @DisplayName("null submitterId → does not exclude anyone, uses system actor")
        void nullSubmitterDoesNotExclude() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            when(userRepository.findEnabledByRoleProfileCodes(RoleProfileCatalog.TASK_MUTATION_ALLOWED_ROLES))
                    .thenReturn(List.of(user(1L, "审核人A")));

            svc.notifyTaskReviewSubmitted(PID, TASK_ID, "任务标题", "提交人", null);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(SYSTEM_USER_ID));
            assertThat(requestCaptor.getValue().recipientUserIds()).containsExactly(1L);
        }

        @Test
        @DisplayName("project not found → no notification")
        void skipsWhenProjectNotFound() {
            svc.notifyTaskReviewSubmitted(PID, TASK_ID, "任务标题", "提交人", UID);

            verify(notificationService, never()).createNotification(any(), any());
        }

        @Test
        @DisplayName("no reviewers → no notification")
        void skipsWhenNoReviewers() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            when(userRepository.findEnabledByRoleProfileCodes(RoleProfileCatalog.TASK_MUTATION_ALLOWED_ROLES))
                    .thenReturn(List.of());

            svc.notifyTaskReviewSubmitted(PID, TASK_ID, "任务标题", "提交人", UID);

            verify(notificationService, never()).createNotification(any(), any());
        }

        @Test
        @DisplayName("notification service throws → does not propagate")
        void handlesNotificationServiceException() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            when(userRepository.findEnabledByRoleProfileCodes(RoleProfileCatalog.TASK_MUTATION_ALLOWED_ROLES))
                    .thenReturn(List.of(user(1L, "审核人A")));
            when(notificationService.createNotification(any(), any())).thenThrow(new RuntimeException("boom"));

            svc.notifyTaskReviewSubmitted(PID, TASK_ID, "任务标题", "提交人", UID);
        }

        // ===== Spec 030 / 06131 案例：按项目可见性过滤接收人 =====

        @Test
        @DisplayName("spec030: bid-Team 用户被广播到无权项目时不出现（06131 案例）")
        void filtersOutInaccessibleRecipients() {
            // 候选 4 人：admin(1)、bid-Teamleader(2)、bid-Team(3 无项目权限)、bid-Team(4 有项目权限)
            // 模拟 06131 场景：bid-Team 用户被广播到无权项目
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("西安地铁")));
            when(userRepository.findEnabledByRoleProfileCodes(RoleProfileCatalog.TASK_MUTATION_ALLOWED_ROLES))
                    .thenReturn(List.of(user(1L, "admin"), user(2L, "组长"),
                                        user(3L, "专员A"), user(4L, "专员B")));
            // user 3 对项目 PID 无访问权（resolver 过滤掉）
            when(recipientResolver.filterByProjectAccess(any(), eq(PID)))
                    .thenReturn(List.of(1L, 2L, 4L));

            svc.notifyTaskReviewSubmitted(PID, TASK_ID, "缴纳保证金", "柏超", UID);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            // user 3 (bid-Team 但无项目权限) 被剔除
            assertThat(requestCaptor.getValue().recipientUserIds())
                    .containsExactlyInAnyOrder(1L, 2L, 4L)
                    .doesNotContain(3L);
        }

        @Test
        @DisplayName("spec030: 所有候选被过滤掉时不创建通知，安全跳过")
        void skipsWhenAllRecipientsFilteredOut() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("欢乐谷")));
            when(userRepository.findEnabledByRoleProfileCodes(RoleProfileCatalog.TASK_MUTATION_ALLOWED_ROLES))
                    .thenReturn(List.of(user(1L, "专员A"), user(2L, "专员B")));
            // 所有候选对项目 PID 都无访问权（极端场景：项目权限收紧后没人可见）
            when(recipientResolver.filterByProjectAccess(any(), eq(PID)))
                    .thenReturn(List.of());

            svc.notifyTaskReviewSubmitted(PID, TASK_ID, "授权整理", "王占俊", UID);

            // 不调用 createNotification（安全跳过，不报错）
            verify(notificationService, never()).createNotification(any(), any());
        }

        @Test
        @DisplayName("spec030: filterByProjectAccess 返回空时安全跳过（resolver 内部已降级）")
        void fallsBackToUnfilteredBroadcast_whenAccessScopeThrows() {
            // 候选 3 人
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("西安地铁")));
            when(userRepository.findEnabledByRoleProfileCodes(RoleProfileCatalog.TASK_MUTATION_ALLOWED_ROLES))
                    .thenReturn(List.of(user(1L, "专员A"), user(2L, "专员B"), user(UID, "提交人")));
            // resolver 内部 DB 故障时降级为原候选广播——这里模拟降级后的结果
            when(recipientResolver.filterByProjectAccess(any(), eq(PID)))
                    .thenReturn(List.of(1L, 2L));

            svc.notifyTaskReviewSubmitted(PID, TASK_ID, "缴纳保证金", "柏超", UID);

            // 降级为原候选广播（排除提交人 UID），优先保证通知送达而非精准
            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            assertThat(requestCaptor.getValue().recipientUserIds())
                    .containsExactlyInAnyOrder(1L, 2L);
        }

        @Test
        @DisplayName("spec030: 过滤后非空时正常派发，targetUrl 不变（仍为 /project/{id}/drafting）")
        void sendsToFilteredRecipients_withUnchangedTargetUrl() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            when(userRepository.findEnabledByRoleProfileCodes(RoleProfileCatalog.TASK_MUTATION_ALLOWED_ROLES))
                    .thenReturn(List.of(user(1L, "admin"), user(2L, "组长"), user(3L, "专员无权")));
            when(recipientResolver.filterByProjectAccess(any(), eq(PID)))
                    .thenReturn(List.of(1L, 2L));

            svc.notifyTaskReviewSubmitted(PID, TASK_ID, "任务标题", "提交人", UID);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            CreateNotificationRequest req = requestCaptor.getValue();
            assertThat(req.recipientUserIds()).containsExactlyInAnyOrder(1L, 2L);
            // targetUrl 保持不变（前端兜底降级由 US2 处理）
            assertThat(req.payload()).containsEntry("targetUrl", "/project/" + PID + "/drafting");
        }
    }

    @Nested
    @DisplayName("notifyTaskReviewResult")
    class TaskReviewResult {

        @Test
        @DisplayName("approved → sends TASK_UPDATE to assignee")
        void approvedSendsToAssignee() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            when(userRepository.findById(ASSIGNEE_ID)).thenReturn(Optional.of(userWithRole("bid-Team")));
            when(effectiveRoleResolver.resolveRoleCode(any(User.class))).thenReturn("bid-Team");

            svc.notifyTaskReviewResult(PID, TASK_ID, "任务标题", ASSIGNEE_ID, true, UID);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            CreateNotificationRequest req = requestCaptor.getValue();
            assertThat(req.type()).isEqualTo("TASK_UPDATE");
            assertThat(req.title()).contains("通过");
            assertThat(req.body()).contains("审核结果：通过");
            assertThat(req.recipientUserIds()).containsExactly(ASSIGNEE_ID);
        }

        @Test
        @DisplayName("rejected → sends TASK_UPDATE to assignee with 驳回 label")
        void rejectedSendsToAssignee() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            when(userRepository.findById(ASSIGNEE_ID)).thenReturn(Optional.of(userWithRole("bid-Team")));
            when(effectiveRoleResolver.resolveRoleCode(any(User.class))).thenReturn("bid-Team");

            svc.notifyTaskReviewResult(PID, TASK_ID, "任务标题", ASSIGNEE_ID, false, UID);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            CreateNotificationRequest req = requestCaptor.getValue();
            assertThat(req.type()).isEqualTo("TASK_UPDATE");
            assertThat(req.title()).contains("驳回");
            assertThat(req.body()).contains("审核结果：驳回");
            assertThat(req.recipientUserIds()).containsExactly(ASSIGNEE_ID);
        }

        @Test
        @DisplayName("bid-otherDept 执行人 → targetUrl 指向 /task-board（CO-474 根因修复）")
        void sendsToBidOtherDeptWithTaskBoardUrl() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            when(userRepository.findById(ASSIGNEE_ID))
                    .thenReturn(Optional.of(userWithRole(RoleProfileCatalog.BID_OTHER_DEPT_CODE)));
            when(effectiveRoleResolver.resolveRoleCode(any(User.class)))
                    .thenReturn(RoleProfileCatalog.BID_OTHER_DEPT_CODE);

            svc.notifyTaskReviewResult(PID, TASK_ID, "任务标题", ASSIGNEE_ID, true, UID);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            CreateNotificationRequest req = requestCaptor.getValue();
            assertThat(req.recipientUserIds()).containsExactly(ASSIGNEE_ID);
            assertThat(req.payload()).containsEntry("targetUrl",
                    "/task-board?taskId=" + TASK_ID + "&projectId=" + PID);
            assertThat(req.payload()).containsEntry("taskId", String.valueOf(TASK_ID));
        }

        @Test
        @DisplayName("bid-Team 执行人 → targetUrl 指向 /project/{id}/drafting（保持历史行为）")
        void sendsToBidTeamWithProjectDraftingUrl() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            when(userRepository.findById(ASSIGNEE_ID)).thenReturn(Optional.of(userWithRole("bid-Team")));
            when(effectiveRoleResolver.resolveRoleCode(any(User.class))).thenReturn("bid-Team");

            svc.notifyTaskReviewResult(PID, TASK_ID, "任务标题", ASSIGNEE_ID, true, UID);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            CreateNotificationRequest req = requestCaptor.getValue();
            assertThat(req.recipientUserIds()).containsExactly(ASSIGNEE_ID);
            assertThat(req.payload()).containsEntry("targetUrl", "/project/" + PID + "/drafting");
        }

        @Test
        @DisplayName("null assigneeId → skipped")
        void skipsWhenAssigneeIsNull() {
            svc.notifyTaskReviewResult(PID, TASK_ID, "任务标题", null, true, UID);

            verify(notificationService, never()).createNotification(any(), any());
        }

        @Test
        @DisplayName("project not found → no notification")
        void skipsWhenProjectNotFound() {
            when(projectRepository.findById(PID)).thenReturn(Optional.empty());

            svc.notifyTaskReviewResult(PID, TASK_ID, "任务标题", ASSIGNEE_ID, true, UID);

            verify(notificationService, never()).createNotification(any(), any());
        }

        @Test
        @DisplayName("assignee 用户不存在 → 仍发送通知但 targetUrl 走兜底 /project/{id}/drafting")
        void sendsWithFallbackUrlWhenAssigneeNotFound() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            when(userRepository.findById(ASSIGNEE_ID)).thenReturn(Optional.empty());

            svc.notifyTaskReviewResult(PID, TASK_ID, "任务标题", ASSIGNEE_ID, true, UID);

            verify(notificationService).createNotification(requestCaptor.capture(), eq(UID));
            CreateNotificationRequest req = requestCaptor.getValue();
            assertThat(req.recipientUserIds()).containsExactly(ASSIGNEE_ID);
            assertThat(req.payload()).containsEntry("targetUrl", "/project/" + PID + "/drafting");
            // assignee 为 null 时不应调用 role resolver
            verify(effectiveRoleResolver, never()).resolveRoleCode(any(User.class));
        }

        @Test
        @DisplayName("notification service throws → does not propagate")
        void handlesNotificationServiceException() {
            when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
            when(userRepository.findById(ASSIGNEE_ID)).thenReturn(Optional.of(userWithRole("bid-Team")));
            when(effectiveRoleResolver.resolveRoleCode(any(User.class))).thenReturn("bid-Team");
            when(notificationService.createNotification(any(), any())).thenThrow(new RuntimeException("boom"));

            svc.notifyTaskReviewResult(PID, TASK_ID, "任务标题", ASSIGNEE_ID, true, UID);
        }
    }
}
