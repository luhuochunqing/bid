package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.common.domain.AuthorizationDecision;
import com.xiyu.bid.matrixcollaboration.repository.ProjectMemberRepository;
import com.xiyu.bid.projectworkflow.core.ProjectDocumentWorkflowPolicy;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentCreateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentDTO;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.security.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class ProjectDocumentWorkflowService {

    private final ProjectWorkflowGuardService guardService;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final UserRepository userRepository;
    private final ProjectLeadAssignmentRepository projectLeadAssignmentRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectDocumentViewAssembler projectDocumentViewAssembler;
    private final ProjectDocumentBindingGateway projectDocumentBindingGateway;
    private final CurrentUserResolver currentUserResolver;

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
        assertCanViewProjectDocuments(projectId);
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
        assertCanUploadProjectDocument();
        Long uploaderId = request.getUploaderId();
        String uploaderName = request.getUploaderName();
        if (uploaderId == null && (uploaderName == null || uploaderName.isBlank())) {
            var currentUser = currentUserResolver.getCurrentUser();
            if (currentUser != null) {
                uploaderId = currentUser.getId();
                uploaderName = currentUser.getFullName();
            }
        } else {
            uploaderName = resolveDisplayName(uploaderId, uploaderName);
        }
        ProjectDocument document = ProjectDocument.builder()
                .projectId(projectId)
                .name(request.getName().trim())
                .size(defaultString(request.getSize(), "1MB"))
                .fileType(trimToNull(request.getFileType()))
                .documentCategory(trimToNull(request.getDocumentCategory()))
                .linkedEntityType(trimToNull(request.getLinkedEntityType()))
                .linkedEntityId(request.getLinkedEntityId())
                .fileUrl(trimToNull(request.getFileUrl()))
                .uploaderId(uploaderId)
                .uploaderName(uploaderName)
                .build();
        ProjectDocument savedDocument = projectDocumentRepository.save(document);
        projectDocumentBindingGateway.onDocumentCreated(savedDocument);
        return projectDocumentViewAssembler.toDto(savedDocument);
    }

    void deleteProjectDocument(Long projectId, Long documentId) {
        guardService.requireWorkflowMutationProject(projectId);

        String roleCode = currentUserResolver.getCurrentRoleCode();
        AuthorizationDecision decision = ProjectDocumentWorkflowPolicy.canDeleteProjectDocument(roleCode);
        if (!decision.allowed()) {
            throw new org.springframework.security.access.AccessDeniedException(decision.reason());
        }

        ProjectDocument document = guardService.requireDocument(projectId, documentId);
        projectDocumentRepository.delete(document);
        projectDocumentBindingGateway.onDocumentDeleted(document);
    }

    private Long[] resolveProjectLeadIds(Long projectId) {
        return projectLeadAssignmentRepository.resolveLeadIdsByProjectId(projectId);
    }

    private Set<Long> resolveProjectMemberIds(Long projectId) {
        return projectMemberRepository.findByProjectId(projectId)
                .stream()
                .map(member -> member.getUserId())
                .collect(Collectors.toSet());
    }

    private void assertCanViewProjectDocuments(Long projectId) {
        var currentUser = currentUserResolver.requireCurrentUser();
        Long[] leadIds = resolveProjectLeadIds(projectId);
        Set<Long> projectMemberIds = resolveProjectMemberIds(projectId);
        AuthorizationDecision decision = ProjectDocumentWorkflowPolicy.canViewProjectDocuments(
                currentUser.getRoleCode(),
                currentUser.getId(),
                leadIds[0],
                leadIds[1],
                projectMemberIds
        );
        if (!decision.allowed()) {
            throw new org.springframework.security.access.AccessDeniedException(decision.reason());
        }
    }

    private void assertCanUploadProjectDocument() {
        String roleCode = currentUserResolver.getCurrentRoleCode();
        AuthorizationDecision decision = ProjectDocumentWorkflowPolicy.canUploadProjectDocument(roleCode);
        if (!decision.allowed()) {
            throw new org.springframework.security.access.AccessDeniedException(decision.reason());
        }
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
