package com.xiyu.bid.project.notification;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.notification.core.NotificationMessagePolicy;
import com.xiyu.bid.notification.core.ProjectNotificationRole;
import com.xiyu.bid.notification.core.TaskNotificationTargetUrlResolver;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.notification.service.NotificationRecipientResolver;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.security.EffectiveRoleResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 任务审核流程通知服务。
 * <p>职责：任务提交审核、审核结果（通过/驳回）的通知派发。</p>
 * <p>提交审核的接收人策略委托给 {@link ProjectNotificationRole}。</p>
 *
 * <p><b>Spec 030 / 06131 案例修复</b>：notifyTaskReviewSubmitted 在派发前按
 * {@link NotificationRecipientResolver#resolveAndFilterProjectRecipients} 过滤候选接收人，
 * 剔除对该项目无访问权的用户。详见 specs/030-fix-task-review-notify-403/。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskReviewNotificationService {

    private static final Long SYSTEM_USER_ID = 0L;

    private final NotificationApplicationService notificationService;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final EffectiveRoleResolver effectiveRoleResolver;
    private final NotificationRecipientResolver recipientResolver;

    /**
     * 通知所有有权限审核任务的人：任务已提交审核。
     *
     * <p>接收人：投标管理员 / 投标组长（有权限审核任务的角色）。</p>
     *
     * <p><b>Spec 030 / 06131 案例修复</b>：派发前按项目可见性过滤候选接收人，
     * 剔除对该项目无访问权的用户（避免被通知的人点击跳转后被 403 拦截）。</p>
     */
    public void notifyTaskReviewSubmitted(Long projectId, Long taskId, String taskTitle,
                                           String submitterName, Long submittedBy) {
        try {
            Project project = projectRepository.findById(projectId).orElse(null);
            if (project == null) return;

            Set<ProjectNotificationRole> roles = Set.of(ProjectNotificationRole.BID_ADMIN, ProjectNotificationRole.BID_TEAM_LEADER);
            List<Long> reviewerIds = recipientResolver.resolveAndFilterProjectRecipients(projectId, roles, submittedBy);
            if (reviewerIds.isEmpty()) {
                log.info("TaskReview notification skipped - no accessible recipients for project {} task {}",
                        projectId, taskId);
                return;
            }

            NotificationMessagePolicy.NotificationMessage message =
                    NotificationMessagePolicy.forTaskReviewSubmitted(
                            project, taskId, taskTitle, submitterName, "/project/" + projectId + "/drafting");
            notificationService.createNotification(new CreateNotificationRequest(
                    message.type(),
                    message.sourceEntityType(),
                    message.sourceEntityId(),
                    message.title(),
                    message.body(),
                    message.payload(),
                    reviewerIds
            ), submittedBy == null ? SYSTEM_USER_ID : submittedBy);
        } catch (RuntimeException e) {
            log.warn("notifyTaskReviewSubmitted failed for project={}, task={}: {}", projectId, taskId, e.getMessage());
        }
    }

    /**
     * 通知任务执行人：审核结果（通过/驳回）。
     * <p>跨部门协同人员（bid-otherDept）的 targetUrl 跳转到任务看板
     * （{@code /task-board?taskId=X&projectId=Y}），其他角色跳转到项目详情页
     * drafting 阶段（{@code /project/{id}/drafting}）。
     * 委托 {@link TaskNotificationTargetUrlResolver} 解析，与
     * {@code ProjectNotificationService.notifyTaskAssigned} 行为对齐（CO-474）。</p>
     */
    public void notifyTaskReviewResult(Long projectId, Long taskId, String taskTitle,
                                        Long assigneeId, boolean approved, Long reviewerId) {
        try {
            if (assigneeId == null) return;
            Project project = projectRepository.findById(projectId).orElse(null);
            if (project == null) return;
            User assignee = userRepository.findById(assigneeId).orElse(null);
            String roleCode = assignee != null ? effectiveRoleResolver.resolveRoleCode(assignee) : null;
            String targetUrl = TaskNotificationTargetUrlResolver.resolveTargetUrl(projectId, taskId, roleCode);
            NotificationMessagePolicy.NotificationMessage message =
                    NotificationMessagePolicy.forTaskReviewResult(
                            project, taskId, taskTitle, approved, targetUrl);
            notificationService.createNotification(new CreateNotificationRequest(
                    message.type(),
                    message.sourceEntityType(),
                    message.sourceEntityId(),
                    message.title(),
                    message.body(),
                    message.payload(),
                    List.of(assigneeId)
            ), reviewerId == null ? SYSTEM_USER_ID : reviewerId);
        } catch (RuntimeException e) {
            log.warn("notifyTaskReviewResult failed for project={}, task={}: {}", projectId, taskId, e.getMessage());
        }
    }
}
