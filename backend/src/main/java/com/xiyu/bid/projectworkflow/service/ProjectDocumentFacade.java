package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.projectworkflow.dto.ProjectDocumentCreateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Component
@RequiredArgsConstructor
class ProjectDocumentFacade {

    private final ProjectDocumentWorkflowService documentWorkflowService;
    private final ProjectDocumentUploadWorkflowService uploadWorkflowService;

    List<ProjectDocumentDTO> getProjectDocuments(Long projectId) {
        return documentWorkflowService.getProjectDocuments(projectId);
    }

    List<ProjectDocumentDTO> getProjectDocuments(
            Long projectId,
            String documentCategory,
            String linkedEntityType,
            Long linkedEntityId
    ) {
        return documentWorkflowService.getProjectDocuments(
                projectId,
                documentCategory,
                linkedEntityType,
                linkedEntityId
        );
    }

    ProjectDocumentDTO createProjectDocument(Long projectId, ProjectDocumentCreateRequest request) {
        return documentWorkflowService.createProjectDocument(projectId, request);
    }

    ProjectDocumentDTO createUploadedProjectDocument(
            Long projectId,
            ProjectDocumentCreateRequest request,
            MultipartFile file
    ) {
        return uploadWorkflowService.createUploadedProjectDocument(projectId, request, file);
    }

    void deleteProjectDocument(Long projectId, Long documentId) {
        documentWorkflowService.deleteProjectDocument(projectId, documentId);
    }
}
