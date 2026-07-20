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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工作台角色化改造：项目待办查询 Service（spec.md §3 模块3）。
 * 从 ProjectService.getWorkbenchTodos 迁出，遵循 FP-Java Profile 单一职责。
 *
 * 角色分支：
 * - admin_lead（admin / /bidAdmin / bid-TeamLeader / bid-SystemAdmin）：
 *   stage = INITIATED 的项目 + 当前用户作为标书审核人的项目
 * - bid-Team（投标专员）：当前用户作为主/副投标负责人的项目，且 stage != CLOSED（已结项不显示）
 * - bid-projectLeader（项目负责人）：stage IN (INITIATED, RETROSPECTIVE) + 标书审核人项目
 * - 其他角色：返回空列表
 *
 * 改进点（相对 ProjectService.getWorkbenchTodos）：
 * 1. P0-2.1：迁移到 workbench 包，路径前缀 /api/workbench 与其他工作台接口对齐
 * 2. P1-2.4：使用 ProjectStage 枚举替代字符串字面量 "INITIATED" / "RETROSPECTIVE" / "CLOSED"
 * 3. P0-2.2：遵循 FP-Java Profile 单一职责，ProjectService 行数从 322 降回 ~232
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

        Set<Long> projectIds = new LinkedHashSet<>();
        String canonicalRole = RoleProfileCatalog.canonicalCode(roleCode);

        if (RoleProfileCatalog.GLOBAL_ACCESS_ROLES.contains(canonicalRole)) {
            // admin_lead 分支：已立项（stage=INITIATED）+ 标书审核人项目
            projectIds.addAll(collectProjectIdsByStages(List.of(ProjectStage.INITIATED)));
            projectIds.addAll(collectReviewerProjectIds(currentUser.getId()));
        } else if (RoleProfileCatalog.BID_SPECIALIST_CODE.equalsIgnoreCase(roleCode)) {
            // 投标专员：主/副投标负责人项目，排除已结项（stage != CLOSED）
            Set<Long> leadProjectIds = new LinkedHashSet<>();
            leadProjectIds.addAll(projectLeadAssignmentRepository
                    .findByPrimaryLeadUserId(currentUser.getId()).stream()
                    .map(ProjectLeadAssignment::getProjectId).toList());
            leadProjectIds.addAll(projectLeadAssignmentRepository
                    .findBySecondaryLeadUserId(currentUser.getId()).stream()
                    .map(ProjectLeadAssignment::getProjectId).toList());
            if (leadProjectIds.isEmpty()) {
                return List.of();
            }
            // 一次查询直接过滤并转 DTO，避免"先查实体取 ID、再按 ID 重查"的重复 DB 往返
            return projectRepository.findAllById(leadProjectIds).stream()
                    .filter(p -> !ProjectStage.CLOSED.name().equals(p.getStage()))
                    .map(ProjectMapper::toDTO)
                    .toList();
        } else if (RoleProfileCatalog.SALES_CODE.equalsIgnoreCase(roleCode)) {
            // 项目负责人：待立项（INITIATED）+ 待结项（RETROSPECTIVE）+ 标书审核人项目
            projectIds.addAll(collectProjectIdsByStages(List.of(ProjectStage.INITIATED, ProjectStage.RETROSPECTIVE)));
            projectIds.addAll(collectReviewerProjectIds(currentUser.getId()));
        } else {
            // 其他角色（bid-otherDept 等）：项目待办不展示
            log.debug("Workbench todos: role {} has no project todos", roleCode);
            return List.of();
        }

        if (projectIds.isEmpty()) {
            return List.of();
        }
        return projectRepository.findAllById(projectIds).stream()
                .map(ProjectMapper::toDTO)
                .toList();
    }

    /** 按 stage 集合查询项目 ID（保持插入顺序，便于稳定排序）。 */
    private Set<Long> collectProjectIdsByStages(List<ProjectStage> stages) {
        return projectRepository.findByStageIn(stages).stream()
                .map(Project::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 查询当前用户作为标书审核人的项目 ID 集合。 */
    private Set<Long> collectReviewerProjectIds(Long userId) {
        return bidDocumentReviewRepository.findByReviewerId(userId).stream()
                .map(BidDocumentReviewEntity::getProjectId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
