package com.xiyu.bid.project.service;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.demo.service.DemoDataProvider;
import com.xiyu.bid.demo.service.DemoFusionService;
import com.xiyu.bid.demo.service.DemoModeService;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.project.dto.ProjectDTO;
import com.xiyu.bid.project.dto.ProjectImportRequest;
import com.xiyu.bid.project.entity.BidDocumentReviewEntity;
import com.xiyu.bid.project.entity.ProjectLeadAssignment;
import com.xiyu.bid.project.repository.BidDocumentReviewRepository;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.security.EffectiveRoleResolver;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectAccessScopeService projectAccessScopeService;
    private final DemoModeService demoModeService;
    private final DemoDataProvider demoDataProvider;
    private final DemoFusionService demoFusionService;
    private final ProjectImportService projectImportService;
    private final ProjectQueryService projectQueryService;
    private final ProjectLeadAssignmentRepository projectLeadAssignmentRepository;
    private final BidDocumentReviewRepository bidDocumentReviewRepository;
    private final ProjectCurrentUserLookupService currentUserLookupService;
    private final EffectiveRoleResolver effectiveRoleResolver;

    @Transactional(readOnly = true)
    public List<ProjectDTO> getAllProjects() {
        return projectQueryService.getAllProjects();
    }

    @Transactional(readOnly = true)
    public ProjectDTO getProjectById(Long id) {
        if (isDemoEntityId(id)) {
            return demoDataProvider.findDemoProjectById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Project", id.toString()));
        }
        projectAccessScopeService.assertCurrentUserCanAccessProject(id);
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id.toString()));
        ProjectDTO dto = ProjectMapper.toDTO(project);
        // CO-387 fix: 详情接口 enrich 主/副投标负责人 ID，供前端权限判断（canDeleteTask/canChangeStatus）
        projectLeadAssignmentRepository.findByProjectId(id).ifPresent(lead -> {
            dto.setPrimaryLeadUserId(lead.getPrimaryLeadUserId());
            dto.setSecondaryLeadUserId(lead.getSecondaryLeadUserId());
        });
        // CO-578: 补充列表投影字段（biddingLeaderName / secondaryBiddingLeaderName /
        // projectLeaderName / leaderDepartment），让详情页公共模块显示投标负责人和辅助人员
        projectQueryService.enrichSingle(dto);
        return dto;
    }

    @Auditable(action = "CREATE_PROJECT", entityType = "Project", description = "创建项目")
    public ProjectDTO createProject(ProjectDTO projectDTO) {
        ProjectDTO normalized = ProjectPayloadValidator.validateAndNormalize(projectDTO, true);
        Project existingProject = ExistingTenderProjectSelector.selectAccessible(
                projectRepository, projectAccessScopeService, normalized.getTenderId());
        if (existingProject != null) return ProjectMapper.toDTO(existingProject);
        Project project = ProjectMapper.toEntity(normalized);
        Project savedProject = projectRepository.save(project);
        return ProjectMapper.toDTO(savedProject);
    }

    public ProjectDTO importProject(ProjectImportRequest request) {
        return projectImportService.importProject(request);
    }

    public ProjectDTO updateProject(Long id, ProjectDTO projectDTO) {
        rejectDemoEntityMutation(id);
        projectAccessScopeService.assertCurrentUserCanAccessProject(id);
        Project existingProject = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id.toString()));
        ProjectDTO normalized = ProjectPayloadValidator.validateAndNormalize(projectDTO, false);
        Project.Status oldStatus = existingProject.getStatus();
        ProjectUpdateApplier.apply(existingProject, normalized, status -> {
            if (existingProject.getStatus().isTerminal()) {
                throw new IllegalArgumentException("Cannot update status of a terminal project");
            }
            if (status.isTerminal() && !"CLOSED".equals(existingProject.getStage())) {
                throw new IllegalArgumentException("Cannot set terminal status unless project stage is CLOSED");
            }
            existingProject.setStatus(status);
        });
        Project updatedProject = projectRepository.save(existingProject);
        publishArchiveEventIfNeeded(oldStatus, updatedProject);
        return ProjectMapper.toDTO(updatedProject);
    }

    public void deleteProject(Long id) {
        rejectDemoEntityMutation(id);
        projectAccessScopeService.assertCurrentUserCanAccessProject(id);
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id.toString()));
        projectRepository.delete(project);
    }

    @Auditable(action = "UPDATE_STATUS", entityType = "Project", description = "更新项目状态")
    public ProjectDTO updateProjectStatus(Long id, Project.Status status) {
        rejectDemoEntityMutation(id);
        projectAccessScopeService.assertCurrentUserCanAccessProject(id);
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id.toString()));
        Project.Status oldStatus = project.getStatus();
        if (status != null) {
            if (status.isTerminal() && !"CLOSED".equals(project.getStage())) {
                throw new IllegalArgumentException("Cannot set terminal status unless project stage is CLOSED");
            }
            project.setStatus(status);
        }
        Project updatedProject = projectRepository.save(project);
        publishArchiveEventIfNeeded(oldStatus, updatedProject);
        return ProjectMapper.toDTO(updatedProject);
    }

    @Auditable(action = "UPDATE_TEAM", entityType = "Project", description = "更新项目团队成员")
    public ProjectDTO updateProjectTeam(Long id, List<Long> teamMembers) {
        rejectDemoEntityMutation(id);
        projectAccessScopeService.assertCurrentUserCanAccessProject(id);
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id.toString()));
        project.setTeamMembers(ProjectPayloadValidator.normalizeTeamMembers(teamMembers));
        return ProjectMapper.toDTO(projectRepository.save(project));
    }

    @Transactional(readOnly = true)
    public List<ProjectDTO> getProjectsByStatus(Project.Status status) {
        return mergeDemoProjectsIfNeeded(
                projectAccessScopeService.filterAccessibleProjects(projectRepository.findByStatus(status)).stream()
                        .map(ProjectMapper::toDTO).collect(Collectors.toList())
        ).stream().filter(item -> item.getStatus() == status).toList();
    }

    @Transactional(readOnly = true)
    public List<ProjectDTO> getProjectsByManager(Long managerId) {
        return mergeDemoProjectsIfNeeded(
                projectAccessScopeService.filterAccessibleProjects(projectRepository.findByManagerId(managerId)).stream()
                        .map(ProjectMapper::toDTO).collect(Collectors.toList())
        ).stream().filter(item -> managerId.equals(item.getManagerId())).toList();
    }

    @Transactional(readOnly = true)
    public List<ProjectDTO> getProjectsByTender(Long tenderId) {
        return mergeDemoProjectsIfNeeded(
                projectAccessScopeService.filterAccessibleProjects(projectRepository.findByTenderId(tenderId)).stream()
                        .map(ProjectMapper::toDTO).collect(Collectors.toList())
        ).stream().filter(item -> tenderId.equals(item.getTenderId())).toList();
    }

    @Transactional(readOnly = true)
    public List<ProjectDTO> getActiveProjects() {
        return mergeDemoProjectsIfNeeded(
                projectAccessScopeService.filterAccessibleProjects(projectRepository.findActiveProjects()).stream()
                        .map(ProjectMapper::toDTO).collect(Collectors.toList())
        ).stream().filter(item -> !item.getStatus().isTerminal()).toList();
    }

    @Transactional(readOnly = true)
    public List<ProjectDTO> searchProjectsByName(String name) {
        String keyword = name == null ? "" : name.toLowerCase();
        return mergeDemoProjectsIfNeeded(
                projectAccessScopeService.filterAccessibleProjects(projectRepository.findByNameContainingIgnoreCase(name)).stream()
                        .map(ProjectMapper::toDTO).collect(Collectors.toList())
        ).stream().filter(item -> item.getName() != null && item.getName().toLowerCase().contains(keyword)).toList();
    }

    @Transactional(readOnly = true)
    public List<ProjectDTO> getProjectsByUpdatedSince(java.time.LocalDateTime since) {
        List<Project> projects = projectRepository.findByUpdatedAtAfter(since);
        return projectAccessScopeService.filterAccessibleProjects(projects).stream()
                .map(ProjectMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<Project.Status, Long> getProjectStatistics() {
        List<Project> visibleProjects = new ArrayList<>(projectAccessScopeService.filterAccessibleProjects(projectRepository.findAll()));
        if (demoModeService.isEnabled()) {
            demoDataProvider.getDemoProjects().forEach(demo -> {
                Project p = new Project();
                p.setStatus(demo.getStatus());
                visibleProjects.add(p);
            });
        }
        return Map.ofEntries(
                Map.entry(Project.Status.PENDING_INITIATION, countByStatus(visibleProjects, Project.Status.PENDING_INITIATION)),
                Map.entry(Project.Status.INITIATED, countByStatus(visibleProjects, Project.Status.INITIATED)),
                Map.entry(Project.Status.BIDDING, countByStatus(visibleProjects, Project.Status.BIDDING)),
                Map.entry(Project.Status.EVALUATING, countByStatus(visibleProjects, Project.Status.EVALUATING)),
                Map.entry(Project.Status.WON, countByStatus(visibleProjects, Project.Status.WON)),
                Map.entry(Project.Status.LOST, countByStatus(visibleProjects, Project.Status.LOST)),
                Map.entry(Project.Status.FAILED, countByStatus(visibleProjects, Project.Status.FAILED)),
                Map.entry(Project.Status.ABANDONED, countByStatus(visibleProjects, Project.Status.ABANDONED))
        );
    }

    private void publishArchiveEventIfNeeded(Project.Status oldStatus, Project updated) {
        // AI 案例沉淀（ProjectClosedEvent）已迁移到 ProjectStageService 在 Stage=CLOSED 转换时发布。
        // 这里仅做项目终态归档状态同步，保留位以备未来扩展。
        if (!oldStatus.isTerminal() && updated.getStatus().isTerminal()) {
            log.info("Project terminal status reached project={} status={}", updated.getId(), updated.getStatus());
        }
    }

    private long countByStatus(List<Project> projects, Project.Status status) {
        return projects.stream().filter(p -> p.getStatus() == status).count();
    }

    private List<ProjectDTO> mergeDemoProjectsIfNeeded(List<ProjectDTO> projects) {
        if (!demoModeService.isEnabled()) return projects;
        return demoFusionService.mergeByKey(projects, demoDataProvider.getDemoProjects(), ProjectDTO::getId);
    }

    private boolean isDemoEntityId(Long id) {
        return demoModeService.isEnabled() && id != null && id < 0;
    }

    private void rejectDemoEntityMutation(Long id) {
        if (isDemoEntityId(id)) throw new IllegalArgumentException("Demo records are read-only in e2e mode");
    }

    // ==================== 工作台角色化改造：项目待办（模块3）====================

    /**
     * 工作台角色化改造：按当前用户角色返回项目待办列表（spec.md §3 模块3）。
     * 角色分支：
     * - admin_lead（admin / /bidAdmin / bid-TeamLeader / bid-SystemAdmin）：
     *   stage = INITIATED 的项目 + 当前用户作为标书审核人的项目
     * - bid-Team（投标专员）：当前用户作为主/副投标负责人的项目，且 stage != CLOSED（已结项不显示）
     * - bid-projectLeader（项目负责人）：stage IN (INITIATED, RETROSPECTIVE) + 标书审核人项目
     * - 其他角色：返回空列表
     * @param userDetails 当前认证用户
     * @return 去重后的项目 DTO 列表
     */
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
            projectIds.addAll(collectProjectIdsByStages(List.of("INITIATED")));
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
            if (!leadProjectIds.isEmpty()) {
                projectRepository.findAllById(leadProjectIds).stream()
                        .filter(p -> !"CLOSED".equals(p.getStage()))
                        .map(Project::getId)
                        .forEach(projectIds::add);
            }
        } else if (RoleProfileCatalog.SALES_CODE.equalsIgnoreCase(roleCode)) {
            // 项目负责人：待立项（INITIATED）+ 待结项（RETROSPECTIVE）+ 标书审核人项目
            projectIds.addAll(collectProjectIdsByStages(List.of("INITIATED", "RETROSPECTIVE")));
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
    private Set<Long> collectProjectIdsByStages(List<String> stages) {
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
