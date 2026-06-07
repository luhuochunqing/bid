package com.xiyu.bid.export.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Encapsulates export access control logic: filtering which projects / tenders
 * the current user is allowed to include in an Excel export.
 */
@RequiredArgsConstructor
public class ExportAccessFilter {

    private final ProjectRepository projectRepository;
    private final ProjectAccessScopeService projectAccessScopeService;

    public Set<Long> exportableProjectIds() {
        if (projectAccessScopeService.currentUserHasAdminAccess()) {
            return null;
        }
        return projectAccessScopeService.filterAccessibleProjects(projectRepository.findAll()).stream()
                .map(Project::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public Set<Long> exportableTenderIds() {
        Set<Long> projectIds = exportableProjectIds();
        if (projectIds == null) {
            return null;
        }
        return projectRepository.findAll().stream()
                .filter(p -> p.getId() != null && projectIds.contains(p.getId()))
                .map(Project::getTenderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public boolean canExportProject(Project project, Set<Long> exportableProjectIds) {
        return exportableProjectIds == null
                || (project != null && project.getId() != null && exportableProjectIds.contains(project.getId()));
    }

    public boolean canExportTender(Tender tender, Set<Long> exportableTenderIds) {
        return exportableTenderIds == null
                || (tender != null && tender.getId() != null && exportableTenderIds.contains(tender.getId()));
    }
}
