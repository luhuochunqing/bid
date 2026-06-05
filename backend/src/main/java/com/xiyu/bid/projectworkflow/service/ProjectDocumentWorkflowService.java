package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.projectworkflow.dto.ProjectDocumentCreateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentDTO;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
class ProjectDocumentWorkflowService {

    private final ProjectWorkflowGuardService guardService;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final UserRepository userRepository;
    private final ProjectDocumentViewAssembler projectDocumentViewAssembler;
    private final ProjectDocumentBindingGateway projectDocumentBindingGateway;

    List<ProjectDocumentDTO> getProjectDocuments(Long projectId) {
        return getProjectDocuments(projectId, null, null, null);
    }

    List<ProjectDocumentDTO> getProjectDocuments(
            Long projectId,
            String documentCategory,
            String linkedEntityType,
            Long linkedEntityId
    ) {
        guardService.requireProject(projectId);
        return projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                        projectId,
                        trimToNull(documentCategory),
                        trimToNull(linkedEntityType),
                        linkedEntityId
                ).stream()
                .map(projectDocumentViewAssembler::toDto)
                .toList();
    }

    ProjectDocumentDTO createProjectDocument(Long projectId, ProjectDocumentCreateRequest request) {
        guardService.requireWorkflowMutationProject(projectId);
        ProjectDocument document = ProjectDocument.builder()
                .projectId(projectId)
                .name(request.getName().trim())
                .size(defaultString(request.getSize(), "1MB"))
                .fileType(trimToNull(request.getFileType()))
                .documentCategory(trimToNull(request.getDocumentCategory()))
                .linkedEntityType(trimToNull(request.getLinkedEntityType()))
                .linkedEntityId(request.getLinkedEntityId())
                .fileUrl(trimToNull(request.getFileUrl()))
                .uploaderId(request.getUploaderId())
                .uploaderName(resolveDisplayName(request.getUploaderId(), request.getUploaderName()))
                .build();
        ProjectDocument savedDocument = projectDocumentRepository.save(document);
        projectDocumentBindingGateway.onDocumentCreated(savedDocument);
        return projectDocumentViewAssembler.toDto(savedDocument);
    }

    void deleteProjectDocument(Long projectId, Long documentId) {
        guardService.requireWorkflowMutationProject(projectId);
        ProjectDocument document = guardService.requireDocument(projectId, documentId);
        projectDocumentRepository.delete(document);
        projectDocumentBindingGateway.onDocumentDeleted(document);
    }

    private String resolveDisplayName(Long userId, String fallback) {
        if (userId != null) {
            var user = userRepository.findById(userId).orElse(null);
            if (user != null && user.getFullName() != null && !user.getFullName().isBlank()) {
                return user.getFullName();
            }
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return "未分配";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultString(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized != null ? normalized : fallback;
    }

}
