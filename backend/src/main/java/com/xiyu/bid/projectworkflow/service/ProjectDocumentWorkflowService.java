package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.common.domain.AuthorizationDecision;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.projectworkflow.core.DocumentCategoryNormalizer;
import com.xiyu.bid.projectworkflow.core.ProjectDocumentWorkflowPolicy;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentCreateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentDTO;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.security.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
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
    private final CurrentUserResolver currentUserResolver;
    private final ProjectLeadAssignmentRepository leadAssignmentRepository;

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
        // CO-474: 补齐 canViewProjectDocuments 闸门（原漏调导致 bid-otherDept 可查看全部项目文档）
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

    /**
     * CO-474: 项目文档查看权限闸门。
     * <p>原 getProjectDocuments 漏调 Policy，导致 bid-otherDept 跨部门协助人员
     * 通过任务分配获得项目访问权后，能查看项目全部文档（违背 RoleProfileCatalog
     * 中 bid-otherDept 的"项目任务处理"dataScope=self 定位）。</p>
     */
    private void assertCanViewProjectDocuments(Long projectId) {
        var currentUser = currentUserResolver.requireCurrentUser();
        String roleCode = currentUserResolver.resolveEffectiveRoleCode(currentUser);
        Long[] leadIds = leadAssignmentRepository.resolveLeadIdsByProjectId(projectId);
        AuthorizationDecision decision = ProjectDocumentWorkflowPolicy.canViewProjectDocuments(
                roleCode, currentUser.getId(), leadIds[0], leadIds[1]);
        if (!decision.allowed()) {
            throw new AccessDeniedException(decision.reason());
        }
    }

    ProjectDocumentDTO createProjectDocument(Long projectId, ProjectDocumentCreateRequest request) {
        guardService.requireProject(projectId);
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
                // CO-420: 归一化 documentCategory 到标准枚举名（TENDER/BID/OPEN_LIST/WIN_NOTICE/DEPOSIT_RECEIPT/OTHER）
                .documentCategory(DocumentCategoryNormalizer.normalize(request.getDocumentCategory()))
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

        ProjectDocument document = guardService.requireDocument(projectId, documentId);
        var currentUser = currentUserResolver.requireCurrentUser();
        String roleCode = currentUserResolver.resolveEffectiveRoleCode(currentUser);
        // CO-383: 上传者本人可删除自己上传的文件（未提交前可重传）
        AuthorizationDecision decision = ProjectDocumentWorkflowPolicy.canDeleteProjectDocument(
                roleCode, currentUser.getId(), document.getUploaderId());
        if (!decision.allowed()) {
            throw new org.springframework.security.access.AccessDeniedException(decision.reason());
        }

        projectDocumentRepository.delete(document);
        projectDocumentBindingGateway.onDocumentDeleted(document);
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
