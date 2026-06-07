package com.xiyu.bid.documentexport.service;

import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class DocumentExportAccessGuard {

    private final ProjectAccessScopeService projectAccessScopeService;

    void requireProjectAccess(Long projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project ID is required");
        }
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
    }
}
