package com.xiyu.bid.project.service;

import com.xiyu.bid.casework.application.ProjectArchiveWorkflowService;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.project.dto.ProjectDTO;
import com.xiyu.bid.project.dto.ProjectImportRequest;
import com.xiyu.bid.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProjectImportService {

    private final ProjectRepository projectRepository;
    private final ProjectArchiveWorkflowService projectArchiveWorkflowService;

    public ProjectDTO importProject(ProjectImportRequest request) {
        validateImportRequest(request);
        Project project = buildProjectFromRequest(request);
        Project saved = projectRepository.save(project);
        archiveIfEnabled(saved.getId(), saved.getName());
        return ProjectMapper.toDTO(saved);
    }

    private void validateImportRequest(ProjectImportRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Project name is required");
        }
        if (request.getTenderId() == null) {
            throw new IllegalArgumentException("Tender ID is required");
        }
        if (request.getManagerId() == null) {
            throw new IllegalArgumentException("Manager ID is required");
        }
    }

    private Project buildProjectFromRequest(ProjectImportRequest request) {
        Project project = new Project();
        project.setName(request.getName().trim());
        project.setTenderId(request.getTenderId());
        project.setManagerId(request.getManagerId());
        project.setStatus(request.getStatus() != null ? request.getStatus() : Project.Status.PENDING_INITIATION);
        project.setStage("INITIATED");
        project.setTeamMembers(request.getTeamMembers() != null ? request.getTeamMembers() : List.of());
        project.setStartDate(request.getStartDate());
        project.setEndDate(request.getEndDate());
        project.setSourceModule(request.getSourceModule());
        project.setSourceCustomerId(request.getSourceCustomerId());
        project.setSourceCustomer(request.getSourceCustomer());
        project.setSourceOpportunityId(request.getSourceOpportunityId());
        project.setCustomer(request.getCustomer());
        project.setBudget(request.getBudget());
        project.setIndustry(request.getIndustry());
        project.setCustomerType(request.getCustomerType());
        project.setRegion(request.getRegion());
        project.setPlatform(request.getPlatform());
        project.setDeadline(request.getDeadline());
        project.setDescription(request.getDescription());
        project.setRemark(request.getRemark());
        applyHistoricalTimestamps(request, project);
        return project;
    }

    private void applyHistoricalTimestamps(ProjectImportRequest request, Project project) {
        if (request.getInitiatedAt() != null) {
            project.setInitiatedAt(request.getInitiatedAt());
        }
        if (request.getEvaluatingAt() != null) {
            project.setEvaluatingAt(request.getEvaluatingAt());
        }
        if (request.getClosedAt() != null) {
            project.setClosedAt(request.getClosedAt());
        }
    }

    private void archiveIfEnabled(Long projectId, String projectName) {
        if (projectArchiveWorkflowService != null) {
            projectArchiveWorkflowService.createArchive(projectId, projectName, "ACTIVE");
        }
    }
}
