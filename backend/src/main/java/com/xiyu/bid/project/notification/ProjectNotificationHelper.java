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
public class ProjectNotificationHelper {

    private final NotificationApplicationService notificationService;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;

    public void notifyInitiationSubmitted(Long projectId, Long submittedBy) {
        try {
            Project project = projectRepository.findById(projectId).orElse(null);
            if (project == null) return;

            String projectName = project.getName();
            String submitterName = userRepository.findById(submittedBy)
                    .map(User::getFullName).orElse("");

            List<Long> adminIds = getAdminUserIds();

            notificationService.createNotification(new CreateNotificationRequest(
                    NotificationType.APPROVAL.name(),
                    "Project",
                    projectId,
                    "立项审核：项目提交立项审核 - " + projectName,
                    String.format("项目名称：%s\n提交人：%s\n\n请前往项目立项页面审核。", projectName, submitterName),
                    Map.of("projectId", String.valueOf(projectId), "projectName", projectName, "targetUrl", "/project/" + projectId + "/initiation"),
                    adminIds
            ), submittedBy);
        } catch (RuntimeException e) {
            log.warn("notifyInitiationSubmitted failed for project={}: {}", projectId, e.getMessage());
        }
    }

    public void notifyInitiationApproved(Long projectId, Long reviewerId) {
        try {
            Project project = projectRepository.findById(projectId).orElse(null);
            if (project == null) return;

            String projectName = project.getName();
            String reviewerName = userRepository.findById(reviewerId)
                    .map(User::getFullName).orElse("");

            List<Long> recipientIds = new ArrayList<>();
            if (project.getManagerId() != null) recipientIds.add(project.getManagerId());

            List<Long> leadIds = getProjectLeadIds(projectId);
            recipientIds.addAll(leadIds);
            recipientIds = recipientIds.stream().distinct().collect(Collectors.toList());

            notificationService.createNotification(new CreateNotificationRequest(
                    NotificationType.INFO.name(),
                    "Project",
                    projectId,
                    "立项审核通过 - " + projectName,
                    String.format("项目名称：%s\n审核人：%s\n\n项目立项已通过审核，请开始标书编制。", projectName, reviewerName),
                    Map.of("projectId", String.valueOf(projectId), "projectName", projectName, "targetUrl", "/project/" + projectId + "/drafting"),
                    recipientIds
            ), reviewerId);
        } catch (RuntimeException e) {
            log.warn("notifyInitiationApproved failed for project={}: {}", projectId, e.getMessage());
        }
    }

    public void notifyInitiationRejected(Long projectId, Long reviewerId, String reason) {
        try {
            Project project = projectRepository.findById(projectId).orElse(null);
            if (project == null) return;

            String projectName = project.getName();
            String reviewerName = userRepository.findById(reviewerId)
                    .map(User::getFullName).orElse("");

            List<Long> recipientIds = new ArrayList<>();
            if (project.getManagerId() != null) recipientIds.add(project.getManagerId());

            notificationService.createNotification(new CreateNotificationRequest(
                    NotificationType.INFO.name(),
                    "Project",
                    projectId,
                    "立项审核驳回 - " + projectName,
                    String.format("项目名称：%s\n审核人：%s\n驳回原因：%s\n\n请修改后重新提交。", projectName, reviewerName, reason),
                    Map.of("projectId", String.valueOf(projectId), "projectName", projectName, "targetUrl", "/project/" + projectId + "/initiation"),
                    recipientIds
            ), reviewerId);
        } catch (RuntimeException e) {
            log.warn("notifyInitiationRejected failed for project={}: {}", projectId, e.getMessage());
        }
    }

    public void notifyStageTransition(Long projectId, ProjectStage fromStage, ProjectStage toStage) {
        try {
            Project project = projectRepository.findById(projectId).orElse(null);
            if (project == null) return;

            String projectName = project.getName();
            List<Long> teamMemberIds = getProjectTeamMemberIds(projectId);

            if (teamMemberIds.isEmpty()) return;

            notificationService.createNotification(new CreateNotificationRequest(
                    NotificationType.INFO.name(),
                    "Project",
                    projectId,
                    "项目阶段变更 - " + projectName,
                    String.format("项目名称：%s\n阶段变更：%s → %s\n\n请关注项目进展。", projectName, fromStage.getDisplayName(), toStage.getDisplayName()),
                    Map.of("projectId", String.valueOf(projectId), "projectName", projectName, "fromStage", fromStage.name(), "toStage", toStage.name(), "targetUrl", "/project/" + projectId),
                    teamMemberIds
            ), null);
        } catch (RuntimeException e) {
            log.warn("notifyStageTransition failed for project={}: {}", projectId, e.getMessage());
        }
    }

    private List<Long> getAdminUserIds() {
        return userRepository.findEnabledByRoleProfileCodes(List.of("admin", "bid_admin", "bid_lead", "bid_senior"))
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
