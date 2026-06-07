package com.xiyu.bid.project.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;

import java.util.List;
import java.util.function.Function;

final class ExistingTenderProjectSelector {

    private ExistingTenderProjectSelector() {
    }

    static Project selectAccessible(
            ProjectRepository projectRepository,
            ProjectAccessScopeService accessScopeService,
            Long tenderId
    ) {
        return selectAccessible(projectRepository.findByTenderId(tenderId), accessScopeService::filterAccessibleProjects);
    }

    static Project selectAccessible(
            List<Project> existingProjects,
            Function<List<Project>, List<Project>> accessFilter
    ) {
        if (existingProjects == null || existingProjects.isEmpty()) return null;
        List<Project> accessibleProjects = accessFilter.apply(existingProjects);
        if (accessibleProjects != null && !accessibleProjects.isEmpty()) return accessibleProjects.get(0);
        throw new IllegalStateException("Tender already has a project");
    }
}
