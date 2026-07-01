package com.xiyu.bid.project.notification;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.matrixcollaboration.entity.ProjectMember;
import com.xiyu.bid.matrixcollaboration.repository.ProjectMemberRepository;
import com.xiyu.bid.notification.core.NotificationType;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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

    public void notifyStageTransition(Long projectId, ProjectStage fromStage, ProjectStage toStage) {
        notifyStageTransition(projectId, fromStage, toStage, SYSTEM_USER_ID);
    }

    public void notifyStageTransition(Long projectId, ProjectStage fromStage, ProjectStage toStage, Long userId) {
        Project project = findProject(projectId);
        if (project == null) return;

        List<Long> teamMemberIds = getProjectTeamMemberIds(projectId);
        if (teamMemberIds.isEmpty()) return;

        sendNotification(projectId, "项目阶段变更", NotificationType.INFO,
                userId == null ? SYSTEM_USER_ID : userId, teamMemberIds, "");
    }

    public void notifyTaskAssigned(Long projectId, Long assigneeId, Long assignedBy) {
        sendNotification(projectId, "任务分配", NotificationType.INFO, assignedBy, List.of(assigneeId), "drafting");
    }

    public void notifyBidReviewResult(Long projectId, Long recipientId, boolean approved, Long reviewerId) {
        if (recipientId == null) return;
        String action = approved ? "通过" : "驳回";
        sendNotification(projectId, "标书审核" + action, NotificationType.INFO, reviewerId, List.of(recipientId), "drafting");
    }

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

    public void notifyEvaluationSubStage(Long projectId, String subStage, Long userId) {
        List<Long> teamMemberIds = getProjectTeamMemberIds(projectId);
        if (teamMemberIds.isEmpty()) return;
        sendNotification(projectId, "评标状态变更", NotificationType.INFO, userId, teamMemberIds, "evaluation");
    }

    public void notifyAbandonBid(Long projectId, Long userId) {
        List<Long> recipientIds = getProjectTeamMemberIds(projectId);
        recipientIds.addAll(getAdminUserIds());
        recipientIds = recipientIds.stream().distinct().collect(Collectors.toList());
        if (recipientIds.isEmpty()) return;
        sendNotification(projectId, "弃标通知", NotificationType.INFO, userId, recipientIds, "evaluation");
    }

    public void notifyResultRegistered(Long projectId, String resultType, Long userId) {
        List<Long> recipientIds = getProjectTeamMemberIds(projectId);
        recipientIds.addAll(getAdminUserIds());
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

    private void sendToAdmins(Long projectId, String title, NotificationType type, Long userId, String targetPage) {
        List<Long> adminIds = getAdminUserIds();
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

    private List<Long> getAdminUserIds() {
        return userRepository.findEnabledByRoleProfileCodes(List.of("admin", "/bidAdmin", "bid-TeamLeader"))
                .stream().map(User::getId).collect(Collectors.toList());
    }

    private List<Long> getProjectLeadIds(Long projectId) {
        List<Long> leadIds = new ArrayList<>();
        projectMemberRepository.findByProjectId(projectId)
                .forEach(member -> {
                    if ("LEAD".equals(member.getPermissionLevel()) || "ADMIN".equals(member.getPermissionLevel())) {
                        leadIds.add(member.getUserId());
                    }
                });
        return leadIds;
    }

    private List<Long> getProjectTeamMemberIds(Long projectId) {
        return projectMemberRepository.findByProjectId(projectId)
                .stream().map(ProjectMember::getUserId).collect(Collectors.toList());
    }
}
