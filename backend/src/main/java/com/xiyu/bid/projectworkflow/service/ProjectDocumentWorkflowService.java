package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.common.domain.AuthorizationDecision;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.projectworkflow.core.DocumentCategoryNormalizer;
import com.xiyu.bid.projectworkflow.core.ProjectDocumentWorkflowPolicy;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentCreateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentDTO;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
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
        // CO-481: 移除 CO-474 引入的第二层 view 闸门，恢复 605ace4a5 设计。
        // 项目文档访问权由 ProjectAccessScopeService（guardService.requireProject）统一负责，不再二次校验 role 白名单。
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
        // CO-487: 结项状态下删除附件报错信息优化——先检查项目状态，给出更友好的提示
        // 必须抛 BusinessException（业务异常），否则会被 GlobalExceptionHandler 当作系统缺陷
        // 吞掉 message，统一返回"系统状态冲突，请刷新后重试"。
        try {
            guardService.requireWorkflowMutationProject(projectId);
        } catch (IllegalStateException e) {
            throw new BusinessException(409, "项目已结项，不可删除文件");
        }

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
                // CO-488: 返回"姓名（工号）"格式，工号取 employeeNumber
                String empNo = user.getEmployeeNumber();
                if (empNo != null && !empNo.isBlank()) {
                    return user.getFullName() + "（" + empNo.trim() + "）";
                }
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
