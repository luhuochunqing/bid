package com.xiyu.bid.project.notification;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.notification.core.NotificationType;
import com.xiyu.bid.notification.core.TaskNotificationTargetUrlResolver;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.security.EffectiveRoleResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务审核流程通知服务。
 * <p>职责：任务提交审核、审核结果（通过/驳回）的通知派发。</p>
 * <p>接收人策略委托给 {@link RoleProfileCatalog#TASK_MUTATION_ALLOWED_ROLES}。</p>
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

    /**
     * 通知所有有权限审核任务的人：任务已提交审核。
     */
    public void notifyTaskReviewSubmitted(Long projectId, Long taskId, String taskTitle,
                                           String submitterName, Long submittedBy) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) return;
        List<Long> reviewerIds = getTaskReviewerUserIds(submittedBy);
        if (reviewerIds.isEmpty()) return;
        String safeTitle = taskTitle != null ? taskTitle : "";
        String safeName = submitterName != null ? submitterName : "";
        String body = String.format("任务：%s\n提交人：%s\n\n该任务已提交审核，请尽快处理。", safeTitle, safeName);
        send(projectId, project.getName(), taskId,
                "任务审核通知 - " + project.getName() + " - " + safeTitle, body,
                reviewerIds, submittedBy, "/project/" + projectId + "/drafting");
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
        if (assigneeId == null) return;
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) return;
        User assignee = userRepository.findById(assigneeId).orElse(null);
        String roleCode = assignee != null ? effectiveRoleResolver.resolveRoleCode(assignee) : null;
        String targetUrl = TaskNotificationTargetUrlResolver.resolveTargetUrl(projectId, taskId, roleCode);
        String safeTitle = taskTitle != null ? taskTitle : "";
        String action = approved ? "通过" : "驳回";
        String body = String.format("任务：%s\n审核结果：%s\n\n您的任务已审核%s，请查看。", safeTitle, action, action);
        send(projectId, project.getName(), taskId,
                "任务审核" + action + " - " + project.getName() + " - " + safeTitle, body,
                List.of(assigneeId), reviewerId, targetUrl);
    }

    private List<Long> getTaskReviewerUserIds(Long excludedUserId) {
        List<Long> ids = userRepository.findEnabledByRoleProfileCodes(RoleProfileCatalog.TASK_MUTATION_ALLOWED_ROLES)
                .stream().map(User::getId).toList();
        if (excludedUserId != null) {
            ids = ids.stream().filter(id -> !id.equals(excludedUserId)).toList();
        }
        return ids;
    }

    private void send(Long projectId, String projectName, Long taskId, String title,
                      String body, List<Long> recipientIds, Long senderId, String targetUrl) {
        try {
            if (recipientIds == null || recipientIds.isEmpty()) return;
            Map<String, Object> payload = new HashMap<>();
            payload.put("projectId", String.valueOf(projectId));
            payload.put("projectName", projectName);
            if (taskId != null) payload.put("taskId", String.valueOf(taskId));
            payload.put("targetUrl", targetUrl);
            notificationService.createNotification(new CreateNotificationRequest(
                    NotificationType.TASK_UPDATE.name(), "PROJECT", projectId,
                    title, body, payload, recipientIds
            ), senderId == null ? SYSTEM_USER_ID : senderId);
        } catch (RuntimeException e) {
            log.warn("TaskReviewNotification failed for project={}: {}", projectId, e.getMessage());
        }
    }
}
