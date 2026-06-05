package com.xiyu.bid.compliance.service;

import com.xiyu.bid.compliance.entity.ComplianceCheckResult;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
class ComplianceProjectAccessGuard {

    private final ProjectRepository projectRepository;
    private final ProjectAccessScopeService projectAccessScopeService;

    void assertCanAccessProject(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
    }

    void assertCanAccessTender(Tender tender) {
        List<Project> linkedProjects = projectRepository.findByTenderId(tender.getId());
        if (linkedProjects.isEmpty() && !projectAccessScopeService.currentUserHasAdminAccess()) {
            throw new AccessDeniedException("权限不足，无法访问未关联项目的标讯");
        }
        for (Project project : linkedProjects) {
            assertCanAccessProject(project.getId());
        }
    }

    void assertCanAccessResult(ComplianceCheckResult result, TenderLoader tenderLoader) {
        if (result.getProjectId() != null) {
            assertCanAccessProject(result.getProjectId());
            return;
        }
        if (result.getTenderId() != null) {
            assertCanAccessTender(tenderLoader.load(result.getTenderId()));
        }
    }

    @FunctionalInterface
    interface TenderLoader {
        Tender load(Long tenderId);
    }
}
