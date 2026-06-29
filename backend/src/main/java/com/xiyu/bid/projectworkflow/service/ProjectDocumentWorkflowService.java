package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.common.domain.AuthorizationDecision;
import com.xiyu.bid.project.repository.BidDocumentReviewRepository;
import com.xiyu.bid.projectworkflow.core.ProjectDocumentWorkflowPolicy;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentCreateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentDTO;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.security.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
class ProjectDocumentWorkflowService {

    private final ProjectWorkflowGuardService guardService;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final UserRepository userRepository;
    private final ProjectLeadAssignmentRepository projectLeadAssignmentRepository;
    private final ProjectDocumentViewAssembler projectDocumentViewAssembler;
    private final ProjectDocumentBindingGateway projectDocumentBindingGateway;
    private final CurrentUserResolver currentUserResolver;
    private final BidDocumentReviewRepository bidDocumentReviewRepository;
    private final TaskRepository taskRepository;

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

    private Long[] resolveProjectLeadIds(Long projectId) {
        return projectLeadAssignmentRepository.resolveLeadIdsByProjectId(projectId);
    }

    private void assertCanViewProjectDocuments(Long projectId) {
        var currentUser = currentUserResolver.requireCurrentUser();
        Long[] leadIds = resolveProjectLeadIds(projectId);
        AuthorizationDecision decision = ProjectDocumentWorkflowPolicy.canViewProjectDocuments(
                currentUserResolver.resolveEffectiveRoleCode(currentUser),
                currentUser.getId(),
                leadIds[0],
                leadIds[1]
        );
        if (decision.allowed()) {
            return;
        }
        // CO-373: 被指定的标书审核人也需要查看投标文件以完成审核
        if (isAssignedReviewer(projectId, currentUser.getId())) {
            return;
        }
        // CO-361: 项目的任务执行人也需要查看投标文件以完成任务交付。
        // 语义对齐：任务执行人已能通过 ProjectAccessScopeService 看到项目、通过 TaskService 看到任务，
        // 文档是完成任务的必要输入；任务分配需经 TaskPermissionGuard.assertCanAssignTask 授权，用户无法自助获取权限。
        if (isProjectTaskAssignee(projectId, currentUser.getId())) {
            return;
        }
        throw new org.springframework.security.access.AccessDeniedException(decision.reason());
    }

    private boolean isAssignedReviewer(Long projectId, Long currentUserId) {
        if (currentUserId == null) {
            return false;
        }
        return bidDocumentReviewRepository.findByProjectId(projectId)
                .map(review -> currentUserId.equals(review.getReviewerId()))
                .orElse(false);
    }

    private boolean isProjectTaskAssignee(Long projectId, Long currentUserId) {
        if (currentUserId == null) {
            return false;
        }
        return taskRepository.existsByProjectIdAndAssigneeId(projectId, currentUserId);
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
