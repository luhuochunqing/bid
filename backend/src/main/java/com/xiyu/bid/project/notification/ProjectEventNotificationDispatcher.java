// Input: Project events (stage transition, archived, task status changed)
// Output: Notifications dispatched via NotificationApplicationService
// Pos: Collaborator/项目事件通知分发
// 维护声明: 仅负责项目事件（阶段流转/归档/任务状态变更）的通知分发；其他通知留在 ProjectNotificationService。
package com.xiyu.bid.project.notification;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.notification.core.NotificationMessagePolicy;
import com.xiyu.bid.notification.core.NotificationType;
import com.xiyu.bid.notification.core.ProjectNotificationRole;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.notification.service.NotificationRecipientResolver;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 项目事件通知分发器。
 *
 * <p>从 ProjectNotificationService 拆分（ResponsibilityArchitectureTest 行预算治理），
 * 负责处理需要构建复杂 payload 的事件通知（阶段流转、归档、任务状态变更）。</p>
 *
 * <p>FP-Java Profile 合规：仅做通知分发编排，不含业务决策。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectEventNotificationDispatcher {

    private static final Long SYSTEM_USER_ID = 0L;

    private final NotificationApplicationService notificationService;
    private final ProjectRepository projectRepository;
    private final NotificationRecipientResolver recipientResolver;

    /**
     * 通知项目团队：阶段自动推进（蓝图 §消息中心-系统通知 序号 6）。
     *
     * <p>接收人：投标管理员 / 投标组长 / 主投标负责人 / 投标辅助人员 / 项目负责人。</p>
     */
    public void notifyStageTransition(Long projectId, ProjectStage fromStage, ProjectStage toStage, Long userId) {
        try {
            Project project = findProject(projectId);
            if (project == null) return;

            Set<ProjectNotificationRole> roles = Set.of(
                    ProjectNotificationRole.BID_ADMIN,
                    ProjectNotificationRole.BID_TEAM_LEADER,
                    ProjectNotificationRole.BID_LEAD,
                    ProjectNotificationRole.BID_ASSISTANT,
                    ProjectNotificationRole.PROJECT_OWNER);
            List<Long> recipientIds = recipientResolver.resolveAndFilterProjectRecipients(projectId, roles, userId);
            if (recipientIds.isEmpty()) return;

            NotificationMessagePolicy.NotificationMessage message = NotificationMessagePolicy.forStageTransition(
                    project, project.getCustomer(), fromStage, toStage, "/project/" + projectId);

            notificationService.createNotification(new CreateNotificationRequest(
                    message.type(),
                    message.sourceEntityType(),
                    message.sourceEntityId(),
                    message.title(),
                    message.body(),
                    message.payload(),
                    recipientIds
            ), userId == null ? SYSTEM_USER_ID : userId);
        } catch (RuntimeException e) {
            log.warn("notifyStageTransition failed for project={}: {}", projectId, e.getMessage());
        }
    }

    /**
     * 通知项目团队：项目已结项归档（蓝图 §消息中心-系统通知 序号 1）。
     *
     * <p>接收人：投标管理员 / 投标组长 / 主投标负责人 / 投标辅助人员。</p>
     */
    public void notifyProjectArchived(Long projectId, String customerName, Long userId) {
        try {
            Project project = findProject(projectId);
            if (project == null) return;

            Set<ProjectNotificationRole> roles = Set.of(
                    ProjectNotificationRole.BID_ADMIN,
                    ProjectNotificationRole.BID_TEAM_LEADER,
                    ProjectNotificationRole.BID_LEAD,
                    ProjectNotificationRole.BID_ASSISTANT);
            List<Long> recipientIds = recipientResolver.resolveAndFilterProjectRecipients(projectId, roles, userId);
            if (recipientIds.isEmpty()) return;

            NotificationMessagePolicy.NotificationMessage message = NotificationMessagePolicy.forProjectArchived(
                    project, customerName, "/project/" + projectId + "/closure");

            notificationService.createNotification(new CreateNotificationRequest(
                    message.type(),
                    message.sourceEntityType(),
                    message.sourceEntityId(),
                    message.title(),
                    message.body(),
                    message.payload(),
                    recipientIds
            ), userId == null ? SYSTEM_USER_ID : userId);
        } catch (RuntimeException e) {
            log.warn("notifyProjectArchived failed for project={}: {}", projectId, e.getMessage());
        }
    }

    /**
     * 通知任务相关人员：任务状态已变更（蓝图 §消息中心-系统通知 序号 2）。
     *
     * <p>接收人：主投标负责人 / 投标辅助人员 / 任务执行人。</p>
     */
    public void notifyTaskStatusChanged(Long projectId, Long taskId, String taskName,
                                        String fromStatus, String toStatus,
                                        Long assigneeId, Long actorUserId) {
        try {
            Project project = findProject(projectId);
            if (project == null) return;

            Set<ProjectNotificationRole> roles = Set.of(
                    ProjectNotificationRole.BID_LEAD,
                    ProjectNotificationRole.BID_ASSISTANT,
                    ProjectNotificationRole.TASK_EXECUTOR);
            List<Long> recipientIds = recipientResolver.resolveAndFilterProjectRecipients(
                    projectId, roles, actorUserId, assigneeId);
            if (recipientIds.isEmpty()) return;

            Task task = Task.builder().id(taskId).title(taskName).projectId(projectId).build();
            NotificationMessagePolicy.NotificationMessage message = NotificationMessagePolicy.forTaskStatusChanged(
                    project, task, fromStatus, toStatus, "/project/" + projectId + "/drafting");

            notificationService.createNotification(new CreateNotificationRequest(
                    message.type(),
                    message.sourceEntityType(),
                    message.sourceEntityId(),
                    message.title(),
                    message.body(),
                    message.payload(),
                    recipientIds
            ), actorUserId == null ? SYSTEM_USER_ID : actorUserId);
        } catch (RuntimeException e) {
            log.warn("notifyTaskStatusChanged failed for project={}, task={}: {}",
                    projectId, taskId, e.getMessage());
        }
    }

    private Project findProject(Long projectId) {
        return projectRepository.findById(projectId).orElse(null);
    }

    /**
     * 通知审核人有新标书待审核（蓝图 §消息中心-系统通知 序号 3）。
     *
     * <p>接收人：当前标书审核记录 reviewerId（单个）。没有对应工厂方法，保留现有文案以保持兼容。</p>
     */
    public void notifyBidReviewSubmitted(Long projectId, Long reviewerId, Long submittedBy,
                                         String tenderTitle, String bidOpeningTime,
                                         String purchaserName, String submitterName) {
        try {
            if (reviewerId == null) return;
            Project project = findProject(projectId);
            if (project == null) return;

            String projectName = project.getName();
            String safeTenderTitle = tenderTitle != null ? tenderTitle : "";
            String safeBidOpeningTime = bidOpeningTime != null ? bidOpeningTime : "";
            String safePurchaserName = purchaserName != null ? purchaserName : "";
            String safeSubmitterName = submitterName != null ? submitterName : "";

            String body = String.format(
                    "项目名称：%s\n招标主体：%s\n开标时间：%s\n提交人：%s\n\n请前往标书制作页面查看投标文件并完成审核。",
                    projectName, safePurchaserName, safeBidOpeningTime, safeSubmitterName);

            Map<String, Object> payload = Map.of(
                    "projectId", projectId,
                    "projectName", projectName,
                    "tenderTitle", safeTenderTitle,
                    "bidOpeningTime", safeBidOpeningTime,
                    "purchaserName", safePurchaserName,
                    "submitterName", safeSubmitterName,
                    "targetUrl", "/project/" + projectId + "/drafting");

            notificationService.createNotification(new CreateNotificationRequest(
                    NotificationType.BID_REVIEW.name(),
                    "PROJECT",
                    projectId,
                    "标书审核：您有一个标书待审核 - " + projectName,
                    body,
                    payload,
                    List.of(reviewerId)
            ), submittedBy);
        } catch (RuntimeException e) {
            log.warn("notifyBidReviewSubmitted failed for project={}: {}", projectId, e.getMessage());
        }
    }
}
