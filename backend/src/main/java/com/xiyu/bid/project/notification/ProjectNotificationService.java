package com.xiyu.bid.project.notification;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.matrixcollaboration.entity.ProjectMember;
import com.xiyu.bid.matrixcollaboration.repository.ProjectMemberRepository;
import com.xiyu.bid.notification.service.NotificationRecipientResolver;
import com.xiyu.bid.notification.core.NotificationType;
import com.xiyu.bid.project.entity.ProjectLeadAssignment;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.notification.core.TaskNotificationTargetUrlResolver;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.notification.core.TaskNotificationTitleFormatter;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.security.EffectiveRoleResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectNotificationService {

    private static final Long SYSTEM_USER_ID = 0L;

    private final NotificationApplicationService notificationService;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectLeadAssignmentRepository leadAssignmentRepository;
    private final EffectiveRoleResolver effectiveRoleResolver;
    private final NotificationRecipientResolver recipientResolver;
    private final ProjectEventNotificationDispatcher eventDispatcher;

    public void notifyInitiationSubmitted(Long projectId, Long submittedBy) {
        sendToAdmins(projectId, "立项审核：项目提交立项审核",
                NotificationType.APPROVAL, submittedBy, "initiation");
    }

    public void notifyInitiationApproved(Long projectId, Long reviewerId) {
        Project project = findProject(projectId);
        if (project == null) return;

        List<Long> recipientIds = new ArrayList<>();
        if (project.getManagerId() != null) recipientIds.add(project.getManagerId());
        recipientIds.addAll(getProjectLeadIds(projectId));
        recipientIds = recipientIds.stream().distinct().collect(Collectors.toList());

        sendNotification(projectId, "立项审核通过", NotificationType.INFO, reviewerId, recipientIds, "drafting");
    }

    public void notifyInitiationRejected(Long projectId, Long reviewerId, String reason) {
        Project project = findProject(projectId);
        if (project == null) return;

        List<Long> recipientIds = new ArrayList<>();
        if (project.getManagerId() != null) recipientIds.add(project.getManagerId());

        sendNotification(projectId, "立项审核驳回", NotificationType.INFO, reviewerId, recipientIds, "initiation");
    }

    /**
     * 通知项目团队：阶段自动推进（蓝图 §消息中心-系统通知 序号 6）。
     * <p>接收人：项目团队成员（投标管理员/投标组长/投标负责人/投标辅助人员/项目负责人）。
     * 渠道：通知中心。频率：实时。</p>
     */
    public void notifyStageTransition(Long projectId, ProjectStage fromStage, ProjectStage toStage) {
        eventDispatcher.notifyStageTransition(projectId, fromStage, toStage, SYSTEM_USER_ID);
    }

    public void notifyStageTransition(Long projectId, ProjectStage fromStage, ProjectStage toStage, Long userId) {
        eventDispatcher.notifyStageTransition(projectId, fromStage, toStage, userId);
    }

    /**
     * 通知被分配人有新任务。
     * <p>跨部门协同人员（bid-otherDept）的 targetUrl 跳转到任务看板
     * （{@code /task-board?taskId=X&projectId=Y}），其他角色跳转到项目详情页
     * drafting 阶段（{@code /project/{id}/drafting}）。</p>
     *
     * <p>根因（CO-474）：原实现对所有角色统一生成 {@code /project/{id}/drafting}，
     * 但 bid-otherDept 角色定位是"项目任务处理"（dataScope=self，只能看 assignee=自己
     * 的任务），不应该进项目详情页查看所有项目文档。前端 {@code /project/:id} 路由
     * 无 permissionKeys 守卫，导致该角色能直接进入项目详情页并查看/下载文档
     * （Bug B：Service 层漏调 ProjectDocumentWorkflowPolicy）。</p>
     *
     * <p>targetUrl 角色判定逻辑已抽取到纯核心类
     * {@link com.xiyu.bid.notification.core.TaskNotificationTargetUrlResolver}，
     * 供本服务与 TaskReviewNotificationService 共用，避免逻辑复制。</p>
     *
     * @param projectId  项目 ID
     * @param taskId     任务 ID（用于构造 task-board 跳转参数）
     * @param assigneeId 被分配人 ID
     * @param assignedBy 分配人 ID（用于审计，可为 0L 表示系统）
     */
    public void notifyTaskAssigned(Long projectId, Long taskId, String taskTitle,
                                    Long assigneeId, Long assignedBy) {
        if (assigneeId == null) return;
        User assignee = userRepository.findById(assigneeId).orElse(null);
        String roleCode = assignee != null ? effectiveRoleResolver.resolveRoleCode(assignee) : null;
        String targetUrl = TaskNotificationTargetUrlResolver.resolveTargetUrl(projectId, taskId, roleCode);
        sendTaskAssignedNotification(projectId, taskId, taskTitle, assigneeId, assignedBy, targetUrl);
    }

    private void sendTaskAssignedNotification(Long projectId, Long taskId, String taskTitle,
                                              Long assigneeId, Long assignedBy, String targetUrl) {
        try {
            Project project = findProject(projectId);
            if (project == null) return;
            String projectName = project.getName();
            String safeTitle = TaskNotificationTitleFormatter.format("任务分配", projectName, taskTitle);
            String body = String.format("项目名称：%s\n任务名称：%s\n\n请关注项目进展。", projectName,
                    taskTitle == null ? "" : taskTitle);
            Map<String, Object> payload = new HashMap<>();
            payload.put("projectId", String.valueOf(projectId));
            payload.put("projectName", projectName);
            if (taskId != null) {
                payload.put("taskId", String.valueOf(taskId));
            }
            payload.put("targetUrl", targetUrl);
            notificationService.createNotification(new CreateNotificationRequest(
                    NotificationType.INFO.name(),
                    "PROJECT",
                    projectId,
                    safeTitle,
                    body,
                    payload,
                    List.of(assigneeId)
            ), assignedBy == null ? SYSTEM_USER_ID : assignedBy);
        } catch (RuntimeException e) {
            log.warn("sendTaskAssignedNotification failed for project={}, task={}: {}",
                    projectId, taskId, e.getMessage());
        }
    }

    public void notifyBidReviewResult(Long projectId, Long recipientId, boolean approved, Long reviewerId) {
        if (recipientId == null) return;
        String action = approved ? "通过" : "驳回";
        sendNotification(projectId, "标书审核" + action, NotificationType.INFO, reviewerId, List.of(recipientId), "drafting");
    }

    public void notifyBidReviewSubmitted(Long projectId, Long reviewerId, Long submittedBy,
                                         String tenderTitle, String bidOpeningTime,
                                         String purchaserName, String submitterName) {
        eventDispatcher.notifyBidReviewSubmitted(projectId, reviewerId, submittedBy,
                tenderTitle, bidOpeningTime, purchaserName, submitterName);
    }

    public void notifyEvaluationSubStage(Long projectId, String subStage, Long userId) {
        List<Long> teamMemberIds = recipientResolver.getProjectMemberUserIds(projectId, null);
        if (teamMemberIds.isEmpty()) return;
        sendNotification(projectId, "评标状态变更", NotificationType.INFO, userId, teamMemberIds, "evaluation");
    }

    public void notifyAbandonBid(Long projectId, Long userId) {
        List<Long> recipientIds = new ArrayList<>(recipientResolver.getProjectMemberUserIds(projectId, null));
        recipientIds.addAll(recipientResolver.getAdminUserIds());
        recipientIds = recipientIds.stream().distinct().collect(Collectors.toList());
        if (recipientIds.isEmpty()) return;
        sendNotification(projectId, "弃标通知", NotificationType.INFO, userId, recipientIds, "evaluation");
    }

    public void notifyResultRegistered(Long projectId, String resultType, Long userId) {
        List<Long> recipientIds = new ArrayList<>(recipientResolver.getProjectMemberUserIds(projectId, null));
        recipientIds.addAll(recipientResolver.getAdminUserIds());
        recipientIds = recipientIds.stream().distinct().collect(Collectors.toList());
        if (recipientIds.isEmpty()) return;
        sendNotification(projectId, "项目结果登记", NotificationType.INFO, userId, recipientIds, "result");
    }

    public void notifyRetrospectiveSubmitted(Long projectId, Long userId) {
        sendToAdmins(projectId, "复盘审核：项目提交复盘", NotificationType.APPROVAL, userId, "retrospective");
    }

    public void notifyRetrospectiveReviewed(Long projectId, Long submitterId, boolean approved, Long reviewerId) {
        if (submitterId == null) return;
        String action = approved ? "通过" : "驳回";
        sendNotification(projectId, "复盘审核" + action, NotificationType.INFO, reviewerId, List.of(submitterId), "retrospective");
    }

    public void notifyClosureSubmitted(Long projectId, Long userId) {
        sendToAdmins(projectId, "结项审核：项目提交结项申请", NotificationType.APPROVAL, userId, "closure");
    }

    public void notifyClosureReviewed(Long projectId, Long submitterId, boolean approved, Long reviewerId) {
        if (submitterId == null) return;
        String action = approved ? "通过" : "驳回";
        sendNotification(projectId, "结项审核" + action, NotificationType.INFO, reviewerId, List.of(submitterId), "closure");
    }

    /**
     * 通知项目团队：项目已结项归档（蓝图 §消息中心-系统通知 序号 1）。
     * <p>触发时机：结项审核通过后。
     * 接收人：投标管理员 + 投标组长 + 投标负责人 + 投标辅助人员（项目团队 + 管理员合并去重）。
     * 渠道：通知中心 + 企微。频率：实时。</p>
     *
     * @param projectId    项目 ID
     * @param customerName 客户名称（用于通知标题，可为 null）
     * @param userId       操作人（审核通过的管理员）
     */
    public void notifyProjectArchived(Long projectId, String customerName, Long userId) {
        eventDispatcher.notifyProjectArchived(projectId, customerName, userId);
    }

    /**
     * 通知任务相关人员：任务状态已变更（蓝图 §消息中心-系统通知 序号 2）。
     * <p>触发时机：任务状态变更（TODO→REVIEW→COMPLETED，或 REVIEW→TODO 驳回）。
     * 接收人：投标负责人 + 投标辅助人员 + 任务执行人（项目团队成员，确保 assignee 在内）。
     * 渠道：通知中心 + 企微。频率：实时。</p>
     *
     * @param projectId    项目 ID
     * @param taskId       任务 ID
     * @param taskName     任务名称
     * @param fromStatus   变更前状态（中文）
     * @param toStatus     变更后状态（中文）
     * @param assigneeId   任务执行人 ID（确保在接收人列表中）
     * @param actorUserId  操作人 ID（排除自己接收）
     */
    public void notifyTaskStatusChanged(Long projectId, Long taskId, String taskName,
                                        String fromStatus, String toStatus,
                                        Long assigneeId, Long actorUserId) {
        eventDispatcher.notifyTaskStatusChanged(projectId, taskId, taskName, fromStatus, toStatus, assigneeId, actorUserId);
    }

    private void sendToAdmins(Long projectId, String title, NotificationType type, Long userId, String targetPage) {
        List<Long> adminIds = recipientResolver.getAdminUserIds();
        sendNotification(projectId, title, type, userId, adminIds, targetPage);
    }

    private void sendNotification(Long projectId, String title, NotificationType type, Long userId, List<Long> recipientIds, String targetPage) {
        try {
            if (recipientIds == null || recipientIds.isEmpty()) return;

            Project project = findProject(projectId);
            if (project == null) return;

            String projectName = project.getName();
            String body = String.format("项目名称：%s\n\n请关注项目进展。", projectName);

            notificationService.createNotification(new CreateNotificationRequest(
                    type.name(),
                    "PROJECT",
                    projectId,
                    title + " - " + projectName,
                    body,
                    Map.of("projectId", String.valueOf(projectId), "projectName", projectName,
                            "targetUrl", "/project/" + projectId + (targetPage.isEmpty() ? "" : "/" + targetPage)),
                    recipientIds
            ), userId);
        } catch (RuntimeException e) {
            log.warn("sendNotification failed for project={}: {}", projectId, e.getMessage());
        }
    }

    private Project findProject(Long projectId) {
        return projectRepository.findById(projectId).orElse(null);
    }

    private List<Long> getProjectLeadIds(Long projectId) {
        List<Long> leadIds = new ArrayList<>();
        projectMemberRepository.findByProjectId(projectId)
                .forEach(member -> {
                    if ("LEAD".equals(member.getPermissionLevel()) || "ADMIN".equals(member.getPermissionLevel())) {
                        leadIds.add(member.getUserId());
                    }
                });
        leadAssignmentRepository.findByProjectId(projectId)
                .ifPresent(assignment -> {
                    if (assignment.getPrimaryLeadUserId() != null) {
                        leadIds.add(assignment.getPrimaryLeadUserId());
                    }
                    if (assignment.getSecondaryLeadUserId() != null) {
                        leadIds.add(assignment.getSecondaryLeadUserId());
                    }
                });
        return leadIds;
    }
}
