package com.xiyu.bid.project.service;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.casework.application.ProjectArchiveWorkflowService;
import com.xiyu.bid.demo.service.DemoDataProvider;
import com.xiyu.bid.demo.service.DemoFusionService;
import com.xiyu.bid.demo.service.DemoModeService;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.project.dto.ProjectDTO;
import com.xiyu.bid.project.dto.ProjectImportRequest;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final ProjectArchiveWorkflowService projectArchiveWorkflowService;
    private final ProjectImportService projectImportService;
    private final ProjectQueryService projectQueryService;

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
        return ProjectMapper.toDTO(project);
    }

    public ProjectDTO createProject(ProjectDTO projectDTO) {
        ProjectDTO normalized = ProjectPayloadValidator.validateAndNormalize(projectDTO, true);
        Project existingProject = ExistingTenderProjectSelector.selectAccessible(
                projectRepository, projectAccessScopeService, normalized.getTenderId());
        if (existingProject != null) return ProjectMapper.toDTO(existingProject);
        Project project = ProjectMapper.toEntity(normalized);
        Project savedProject = projectRepository.save(project);
        if (projectArchiveWorkflowService != null) {
            projectArchiveWorkflowService.createArchive(savedProject.getId(), savedProject.getName(), "ACTIVE");
        }
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
}
