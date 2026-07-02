package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.common.domain.AuthorizationDecision;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.project.core.BidReadinessPolicy;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.project.service.ProjectStageService;
import com.xiyu.bid.projectworkflow.core.ProjectDocumentWorkflowPolicy;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentDownloadFile;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.security.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class ProjectDocumentDownloadService {

    private final ProjectWorkflowGuardService accessGuard;
    private final ProjectDocumentFileStorage fileStorage;
    private final ProjectStageService projectStageService;
    private final CurrentUserResolver currentUserResolver;
    private final ProjectLeadAssignmentRepository leadAssignmentRepository;

    ProjectDocumentDownloadFile getProjectDocumentFile(Long projectId, Long documentId) {
        accessGuard.requireProject(projectId);
        ProjectDocument document = accessGuard.requireDocument(projectId, documentId);
        // CO-474: 补齐 canDownloadProjectDocument 闸门（原漏调导致 bid-otherDept 可下载项目文档）
        assertCanDownloadProjectDocument(projectId);
        // CO-381: 投标文件（BID 类型）在标书制作阶段结束后只读不可下载。
    // 项目仍处于 DRAFTING 阶段（含已 submit-review 进入 REVIEWING 子状态）时，投标负责人/审核人可正常下载。
    // CO-442: 结项（CLOSED）后允许下载投标文件，作为知识库积累。
    assertBidDocumentDownloadable(projectId, document);
        String fileUrl = trimToNull(document.getFileUrl());
        if (fileUrl == null) {
            throw ResourceNotFoundException.withMessage("Project document file not found: " + documentId);
        }
        LoadedProjectDocumentFile loaded = fileStorage.load(fileUrl)
                .orElseThrow(() -> ResourceNotFoundException.withMessage("Project document file not found: " + documentId));
        byte[] content = loaded.content() == null ? new byte[0] : loaded.content();
        return new ProjectDocumentDownloadFile(
                defaultString(document.getName(), "项目文档"),
                loaded.fileUrl(),
                loaded.physicalPath(),
                defaultString(loaded.contentType(), resolveContentType(document.getFileType(), document.getName())),
                content.length,
                loaded.resource() == null ? new ByteArrayResource(content) : loaded.resource()
        );
    }

    /**
     * CO-474: 项目文档下载权限闸门。
     * <p>原 getProjectDocumentFile 漏调 Policy，导致 bid-otherDept 跨部门协助人员
     * 通过任务分配获得项目访问权后，能下载项目文档。权限矩阵与 canViewProjectDocuments
     * 对称（参见 lessons-learned.md §24 权限矩阵对称设计要求）。</p>
     */
    private void assertCanDownloadProjectDocument(Long projectId) {
        var currentUser = currentUserResolver.requireCurrentUser();
        String roleCode = currentUserResolver.resolveEffectiveRoleCode(currentUser);
        Long[] leadIds = leadAssignmentRepository.resolveLeadIdsByProjectId(projectId);
        AuthorizationDecision decision = ProjectDocumentWorkflowPolicy.canDownloadProjectDocument(
                roleCode, currentUser.getId(), leadIds[0], leadIds[1]);
        if (!decision.allowed()) {
            throw new AccessDeniedException(decision.reason());
        }
    }

    private void assertBidDocumentDownloadable(Long projectId, ProjectDocument document) {
        if (!BidReadinessPolicy.BID_DOCUMENT_CATEGORY.equals(document.getDocumentCategory())) {
            return;
        }
        ProjectStage stage = projectStageService.currentStage(projectId);
        // CO-442: DRAFTING（含 submit-review 子状态）和 CLOSED（结项后知识库积累）允许下载；
        // 中间阶段（EVALUATING/RESULT_PENDING/RETROSPECTIVE）只读不可下载，防止标书扩散。
        if (stage != ProjectStage.DRAFTING && stage != ProjectStage.CLOSED) {
            // 409 Conflict：与 ProjectStageService 阶段非法跳转的语义对齐——
            // 请求与项目当前阶段状态冲突。不用 423 LOCKED（WebDAV 语义不符）。
            throw new BusinessException(409,
                    "投标文件已进入「" + stage.getDisplayName() + "」阶段，文件只读不可下载");
        }
    }

    private String resolveContentType(String fileType, String fileName) {
        String normalized = trimToNull(fileType);
        if (normalized != null && normalized.contains("/")) {
            return MediaType.parseMediaType(normalized).toString();
        }
        String candidateName = normalized == null ? fileName : "document." + normalized;
        return MediaTypeFactory.getMediaType(candidateName)
                .orElse(MediaType.APPLICATION_OCTET_STREAM)
                .toString();
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String defaultString(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized != null ? normalized : fallback;
    }
}
