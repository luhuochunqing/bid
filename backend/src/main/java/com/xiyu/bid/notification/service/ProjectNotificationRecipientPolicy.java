package com.xiyu.bid.notification.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.matrixcollaboration.entity.ProjectMember;
import com.xiyu.bid.matrixcollaboration.repository.ProjectMemberRepository;
import com.xiyu.bid.notification.core.ProjectNotificationRole;
import com.xiyu.bid.project.entity.BidDocumentReviewEntity;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.entity.ProjectLeadAssignment;
import com.xiyu.bid.project.repository.BidDocumentReviewRepository;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 项目级通知接收人策略 —— 按项目角色解析接收人用户 ID 列表。
 *
 * <p>应用服务策略类：负责项目维度通知接收人的查询编排与决策，不写状态、不做 DTO 转换。
 * 所有 Repository 调用各自 try-catch，异常时降级为继续处理已收集结果，
 * 符合 Constitution VII §2 "装饰性操作失败必须降级"。</p>
 *
 * <p>输出保证：去重、顺序稳定（按 {@link ProjectNotificationRole} 枚举声明顺序叠加各角色结果），
 * 并排除 {@code excludeUserId}。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectNotificationRecipientPolicy {

    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectLeadAssignmentRepository projectLeadAssignmentRepository;
    private final ProjectInitiationDetailsRepository projectInitiationDetailsRepository;
    private final BidDocumentReviewRepository bidDocumentReviewRepository;

    /**
     * 解析项目通知接收人列表。
     *
     * @param projectId     项目 ID
     * @param roles         目标角色集合（为 null 或空时返回空列表）
     * @param excludeUserId 要排除的用户 ID（通常为操作人自己，可为 null）
     * @return 去重、保序后的接收人用户 ID 列表；任意 Repository 异常时降级返回已收集结果
     */
    public List<Long> resolveRecipients(Long projectId, Set<ProjectNotificationRole> roles, Long excludeUserId) {
        return resolveRecipients(projectId, roles, excludeUserId, null);
    }

    /**
     * 解析项目通知接收人列表（支持任务执行人角色）。
     *
     * @param projectId      项目 ID
     * @param roles          目标角色集合（为 null 或空时返回空列表）
     * @param excludeUserId  要排除的用户 ID（通常为操作人自己，可为 null）
     * @param taskExecutorId 任务执行人用户 ID（仅在 roles 包含 {@link ProjectNotificationRole#TASK_EXECUTOR} 时使用，可为 null）
     * @return 去重、保序后的接收人用户 ID 列表；任意 Repository 异常时降级返回已收集结果
     */
    public List<Long> resolveRecipients(Long projectId, Set<ProjectNotificationRole> roles, Long excludeUserId, Long taskExecutorId) {
        if (projectId == null || roles == null || roles.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<Long> result = new LinkedHashSet<>();

        // 按枚举声明顺序遍历，确保输出顺序稳定，不受调用方 Set 实现影响。
        for (ProjectNotificationRole role : ProjectNotificationRole.values()) {
            if (!roles.contains(role)) {
                continue;
            }
            switch (role) {
                case BID_ADMIN -> collectUserIdsByRoleCode(role.roleCode(), result);
                case BID_TEAM_LEADER -> collectUserIdsByRoleCode(role.roleCode(), result);
                case BID_LEAD -> collectPrimaryLead(projectId, result);
                case BID_ASSISTANT -> collectSecondaryLead(projectId, result);
                case PROJECT_OWNER -> collectProjectOwner(projectId, result);
                case TASK_EXECUTOR -> addIfNotNull(taskExecutorId, result);
                case BID_REVIEWER -> collectBidReviewer(projectId, result);
                case PROJECT_MEMBER -> collectProjectMembers(projectId, result);
            }
        }

        result.remove(excludeUserId);
        return List.copyOf(result);
    }

    private void collectUserIdsByRoleCode(String roleCode, LinkedHashSet<Long> result) {
        if (roleCode == null) {
            return;
        }
        try {
            userRepository.findEnabledByRoleProfileCodes(List.of(roleCode)).stream()
                    .map(User::getId)
                    .filter(Objects::nonNull)
                    .forEach(result::add);
        } catch (RuntimeException e) {
            log.warn("collectUserIdsByRoleCode failed for code={}, continuing: {}", roleCode, e.getMessage());
        }
    }

    private void collectPrimaryLead(Long projectId, LinkedHashSet<Long> result) {
        try {
            projectLeadAssignmentRepository.findByProjectId(projectId)
                    .map(ProjectLeadAssignment::getPrimaryLeadUserId)
                    .ifPresent(result::add);
        } catch (RuntimeException e) {
            log.warn("collectPrimaryLead failed for projectId={}, continuing: {}", projectId, e.getMessage());
        }
    }

    private void collectSecondaryLead(Long projectId, LinkedHashSet<Long> result) {
        try {
            projectLeadAssignmentRepository.findByProjectId(projectId)
                    .map(ProjectLeadAssignment::getSecondaryLeadUserId)
                    .ifPresent(result::add);
        } catch (RuntimeException e) {
            log.warn("collectSecondaryLead failed for projectId={}, continuing: {}", projectId, e.getMessage());
        }
    }

    private void collectProjectOwner(Long projectId, LinkedHashSet<Long> result) {
        try {
            projectInitiationDetailsRepository.findByProjectId(projectId)
                    .map(ProjectInitiationDetails::getOwnerUserId)
                    .ifPresent(result::add);
        } catch (RuntimeException e) {
            log.warn("collectProjectOwner failed for projectId={}, continuing: {}", projectId, e.getMessage());
        }
    }

    private void collectBidReviewer(Long projectId, LinkedHashSet<Long> result) {
        try {
            bidDocumentReviewRepository.findByProjectId(projectId)
                    .map(BidDocumentReviewEntity::getReviewerId)
                    .ifPresent(result::add);
        } catch (RuntimeException e) {
            log.warn("collectBidReviewer failed for projectId={}, continuing: {}", projectId, e.getMessage());
        }
    }

    private void collectProjectMembers(Long projectId, LinkedHashSet<Long> result) {
        try {
            projectMemberRepository.findByProjectId(projectId).stream()
                    .map(ProjectMember::getUserId)
                    .filter(Objects::nonNull)
                    .forEach(result::add);
        } catch (RuntimeException e) {
            log.warn("collectProjectMembers failed for projectId={}, continuing: {}", projectId, e.getMessage());
        }
    }

    private void addIfNotNull(Long userId, LinkedHashSet<Long> result) {
        if (userId != null) {
            result.add(userId);
        }
    }
}
