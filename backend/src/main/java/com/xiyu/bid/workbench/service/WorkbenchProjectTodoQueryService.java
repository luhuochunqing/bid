package com.xiyu.bid.workbench.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.dto.ProjectDTO;
import com.xiyu.bid.project.entity.BidDocumentReviewEntity;
import com.xiyu.bid.project.entity.ProjectLeadAssignment;
import com.xiyu.bid.project.repository.BidDocumentReviewRepository;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.project.service.ProjectMapper;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.security.CurrentUserLookupService;
import com.xiyu.bid.security.EffectiveRoleResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工作台角色化改造：项目待办查询 Service（spec.md §3 模块3）。
 *
 * 角色分支（2026-07-20 调整：标书审核人项目仅限 DRAFTING 阶段 + REVIEWING 状态）：
 * - admin_lead：已立项（INITIATED）+ 投标中（DRAFTING 限待审核标书）
 * - bid-team（投标专员）：主/副负责人项目（排除 CLOSED）+ 待审核标书项目（投标中）
 * - bid-projectLeader（项目负责人）：待立项（INITIATED）+ 待结项（RETROSPECTIVE）+ 投标中（DRAFTING 限待审核标书）
 * - 其他角色：返回空列表
 *
 * todoLabel 中文标签按角色+阶段计算：
 * - admin_lead: INITIATED→"已立项", DRAFTING→"投标中"
 * - sales: INITIATED→"待立项", RETROSPECTIVE→"待结项", DRAFTING→"投标中"
 * - bid_team: 按项目实际阶段映射中文（INITIATED→"已立项", DRAFTING→"投标中" 等）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkbenchProjectTodoQueryService {

    private final ProjectRepository projectRepository;
    private final ProjectLeadAssignmentRepository projectLeadAssignmentRepository;
    private final BidDocumentReviewRepository bidDocumentReviewRepository;
    private final CurrentUserLookupService currentUserLookupService;
    private final EffectiveRoleResolver effectiveRoleResolver;

    @Transactional(readOnly = true)
    public List<ProjectDTO> getWorkbenchTodos(UserDetails userDetails) {
        User currentUser = currentUserLookupService.requireUser(userDetails);
        String roleCode = effectiveRoleResolver.resolveRoleCode(currentUser);
        // fail-closed：OSS 缓存未命中时 roleCode 为 null，返回空列表避免误放行
        if (roleCode == null) {
            log.warn("Workbench todos: role code resolution failed for user {} (OSS cache miss)",
                    currentUser.getUsername());
            return List.of();
        }

        String canonicalRole = RoleProfileCatalog.canonicalCode(roleCode);
        Long userId = currentUser.getId();

        // 待审核标书的项目 ID（DRAFTING 阶段 + REVIEWING 状态）
        Set<Long> pendingReviewProjectIds = collectPendingReviewProjectIds(userId);

        // projectId → todoLabel 映射（保持插入顺序，待审核标书的"投标中"优先级最高）
        Map<Long, String> projectIdToLabel = new LinkedHashMap<>();

        if (RoleProfileCatalog.GLOBAL_ACCESS_ROLES.contains(canonicalRole)) {
            // admin_lead: 已立项（INITIATED）+ 投标中（DRAFTING 限待审核标书）
            collectProjectIdsByStages(List.of(ProjectStage.INITIATED))
                    .forEach(id -> projectIdToLabel.putIfAbsent(id, "已立项"));
            pendingReviewProjectIds.forEach(id -> projectIdToLabel.put(id, "投标中"));
        } else if (RoleProfileCatalog.BID_SPECIALIST_CODE.equalsIgnoreCase(roleCode)) {
            // 投标专员: 主/副负责人项目（排除 CLOSED）+ 待审核标书项目（投标中）
            Set<Long> leadProjectIds = new LinkedHashSet<>();
            leadProjectIds.addAll(projectLeadAssignmentRepository
                    .findByPrimaryLeadUserId(userId).stream()
                    .map(ProjectLeadAssignment::getProjectId).toList());
            leadProjectIds.addAll(projectLeadAssignmentRepository
                    .findBySecondaryLeadUserId(userId).stream()
                    .map(ProjectLeadAssignment::getProjectId).toList());
            if (!leadProjectIds.isEmpty()) {
                projectRepository.findAllById(leadProjectIds).stream()
                        .filter(p -> !ProjectStage.CLOSED.name().equals(p.getStage()))
                        .forEach(p -> projectIdToLabel.putIfAbsent(p.getId(), resolveStageLabel(p.getStage())));
            }
            pendingReviewProjectIds.forEach(id -> projectIdToLabel.put(id, "投标中"));
        } else if (RoleProfileCatalog.SALES_CODE.equalsIgnoreCase(roleCode)) {
            // 项目负责人: 待立项（INITIATED）+ 待结项（RETROSPECTIVE）+ 投标中（DRAFTING 限待审核标书）
            collectProjectIdsByStages(List.of(ProjectStage.INITIATED))
                    .forEach(id -> projectIdToLabel.putIfAbsent(id, "待立项"));
            collectProjectIdsByStages(List.of(ProjectStage.RETROSPECTIVE))
                    .forEach(id -> projectIdToLabel.putIfAbsent(id, "待结项"));
            pendingReviewProjectIds.forEach(id -> projectIdToLabel.put(id, "投标中"));
        } else {
            // 其他角色（bid-otherDept 等）：项目待办不展示
            log.debug("Workbench todos: role {} has no project todos", roleCode);
            return List.of();
        }

        if (projectIdToLabel.isEmpty()) {
            return List.of();
        }
        return projectRepository.findAllById(projectIdToLabel.keySet()).stream()
                .map(p -> {
                    ProjectDTO dto = ProjectMapper.toDTO(p);
                    dto.setTodoLabel(projectIdToLabel.get(p.getId()));
                    return dto;
                })
                .toList();
    }

    /** 按 stage 集合查询项目 ID（保持插入顺序，便于稳定排序）。 */
    private Set<Long> collectProjectIdsByStages(List<ProjectStage> stages) {
        return projectRepository.findByStageIn(stages).stream()
                .map(Project::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 查询当前用户待审核标书的项目 ID（DRAFTING 阶段 + REVIEWING 状态）。
     * "待自己审核标书"= BidDocumentReview.reviewerId = userId AND status = REVIEWING
     * 且项目处于 DRAFTING（投标中）阶段。
     */
    private Set<Long> collectPendingReviewProjectIds(Long userId) {
        Set<Long> projectIds = bidDocumentReviewRepository
                .findByReviewerIdAndStatus(userId, "REVIEWING").stream()
                .map(BidDocumentReviewEntity::getProjectId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (projectIds.isEmpty()) {
            return Set.of();
        }
        // 过滤 DRAFTING 阶段（投标中）
        return projectRepository.findAllById(projectIds).stream()
                .filter(p -> ProjectStage.DRAFTING.name().equals(p.getStage()))
                .map(Project::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 投标专员：按项目实际阶段映射中文标签。 */
    private String resolveStageLabel(String stage) {
        if (stage == null) return "";
        return switch (stage) {
            case "INITIATED" -> "已立项";
            case "DRAFTING" -> "投标中";
            case "EVALUATING" -> "评标中";
            case "RESULT_PENDING" -> "待结果";
            case "RETROSPECTIVE" -> "待结项";
            case "CLOSED" -> "已结项";
            default -> stage;
        };
    }
}
