// Input: Project events (stage transition, archived, task status changed)
// Output: Notifications dispatched via NotificationApplicationService
// Pos: Collaborator/项目事件通知分发
// 维护声明: 仅负责项目事件（阶段流转/归档/任务状态变更）的通知分发；其他通知留在 ProjectNotificationService。
package com.xiyu.bid.project.notification;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.notification.core.NotificationType;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.notification.service.NotificationRecipientResolver;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
     */
    public void notifyStageTransition(Long projectId, ProjectStage fromStage, ProjectStage toStage, Long userId) {
        try {
            Project project = findProject(projectId);
            if (project == null) return;

            List<Long> teamMemberIds = recipientResolver.getProjectMemberUserIds(projectId, null);
            if (teamMemberIds.isEmpty()) return;

            String projectName = project.getName();
            String customerName = project.getCustomer();
            String prefix = (customerName != null && !customerName.isBlank())
                    ? String.format("【%s - %s】", customerName, projectName)
                    : String.format("【%s】", projectName);
            String fromLabel = fromStage != null ? fromStage.getDisplayName() : "未知";
            String toLabel = toStage != null ? toStage.getDisplayName() : "未知";
            String body = String.format("%s阶段发生自动流转：%s → %s", prefix, fromLabel, toLabel);

            Map<String, Object> payload = new HashMap<>();
            payload.put("projectId", String.valueOf(projectId));
            payload.put("projectName", projectName);
            payload.put("fromStage", fromStage != null ? fromStage.name() : "");
            payload.put("toStage", toStage != null ? toStage.name() : "");
            payload.put("targetUrl", "/project/" + projectId);

            notificationService.createNotification(new CreateNotificationRequest(
                    NotificationType.SYSTEM.name(),
                    "PROJECT",
                    projectId,
                    "阶段变更 - " + projectName,
                    body,
                    payload,
                    teamMemberIds
            ), userId == null ? SYSTEM_USER_ID : userId);
        } catch (RuntimeException e) {
            log.warn("notifyStageTransition failed for project={}: {}", projectId, e.getMessage());
        }
    }

    /**
     * 通知项目团队：项目已结项归档（蓝图 §消息中心-系统通知 序号 1）。
     */
    public void notifyProjectArchived(Long projectId, String customerName, Long userId) {
        try {
            Project project = findProject(projectId);
            if (project == null) return;

            String projectName = project.getName();
            String prefix = (customerName != null && !customerName.isBlank())
                    ? String.format("【%s - %s】", customerName, projectName)
                    : String.format("【%s】", projectName);
            String body = prefix + "已结项归档，所有字段锁定，资料已自动归档";

            List<Long> recipientIds = new ArrayList<>(recipientResolver.getProjectMemberUserIds(projectId, null));
            recipientIds.addAll(recipientResolver.getAdminUserIds());
            recipientIds = recipientIds.stream().distinct().collect(Collectors.toList());
            if (recipientIds.isEmpty()) return;

            Map<String, Object> payload = new HashMap<>();
            payload.put("projectId", String.valueOf(projectId));
            payload.put("projectName", projectName);
            payload.put("targetUrl", "/project/" + projectId + "/closure");

            notificationService.createNotification(new CreateNotificationRequest(
                    NotificationType.SYSTEM.name(),
                    "PROJECT",
                    projectId,
                    "项目结项归档 - " + projectName,
                    body,
                    payload,
                    recipientIds
            ), userId == null ? SYSTEM_USER_ID : userId);
        } catch (RuntimeException e) {
            log.warn("notifyProjectArchived failed for project={}: {}", projectId, e.getMessage());
        }
    }

    /**
     * 通知任务相关人员：任务状态已变更（蓝图 §消息中心-系统通知 序号 2）。
     */
    public void notifyTaskStatusChanged(Long projectId, Long taskId, String taskName,
                                        String fromStatus, String toStatus,
                                        Long assigneeId, Long actorUserId) {
        try {
            Project project = findProject(projectId);
            if (project == null) return;

            String projectName = project.getName();
            String body = String.format("【%s】任务「%s」状态发生变更：%s → %s",
                    projectName, taskName != null ? taskName : "", fromStatus, toStatus);

            List<Long> recipientIds = new ArrayList<>(recipientResolver.getProjectMemberUserIds(projectId, actorUserId));
            if (assigneeId != null && !recipientIds.contains(assigneeId)
                    && !assigneeId.equals(actorUserId)) {
                recipientIds.add(assigneeId);
            }
            if (recipientIds.isEmpty()) return;

            Map<String, Object> payload = new HashMap<>();
            payload.put("projectId", String.valueOf(projectId));
            payload.put("projectName", projectName);
            payload.put("taskId", String.valueOf(taskId));
            payload.put("taskName", taskName != null ? taskName : "");
            payload.put("fromStatus", fromStatus);
            payload.put("toStatus", toStatus);
            payload.put("targetUrl", "/project/" + projectId + "/drafting");

            notificationService.createNotification(new CreateNotificationRequest(
                    NotificationType.TASK_UPDATE.name(),
                    "PROJECT",
                    projectId,
                    "任务状态变更 - " + projectName,
                    body,
                    payload,
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
                    "projectId", String.valueOf(projectId),
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
