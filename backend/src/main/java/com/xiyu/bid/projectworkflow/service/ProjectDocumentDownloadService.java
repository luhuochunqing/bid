package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.file.application.ObsShareUrlSigner;
import com.xiyu.bid.project.core.BidReadinessPolicy;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.service.ProjectStageService;
import com.xiyu.bid.projectworkflow.core.ProjectDocumentStorageType;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentDownloadFile;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;

/**
 * 项目文档下载服务。
 *
 * <p>按 fileUrl 存储类型分派下载路径：
 * <ul>
 *   <li>OBS 直传（obs-direct:）→ 生成预签名 URL，Controller 返回 302 重定向</li>
 *   <li>本地存储（bid-agent:// / doc-insight://）→ 流式返回 200</li>
 *   <li>未知/空 → 404</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
class ProjectDocumentDownloadService {

    private final ProjectWorkflowGuardService accessGuard;
    private final ProjectDocumentFileStorage fileStorage;
    private final ProjectStageService projectStageService;
    private final ObsShareUrlSigner obsShareUrlSigner;

    ProjectDocumentDownloadFile getProjectDocumentFile(Long projectId, Long documentId) {
        accessGuard.requireProject(projectId);
        ProjectDocument document = accessGuard.requireDocument(projectId, documentId);
        // CO-481: 项目文档下载权由 ProjectAccessScopeService（accessGuard.requireProject）统一负责。
        // CO-381: 投标文件（BID 类型）阶段只读校验；DRAFTING（含 submit-review REVIEWING）和 CLOSED 允许下载。
        assertBidDocumentDownloadable(projectId, document);

        String fileUrl = trimToNull(document.getFileUrl());
        if (fileUrl == null) {
            throw ResourceNotFoundException.withMessage("Project document file not found: " + documentId);
        }

        String fileName = resolveFileName(document);
        ProjectDocumentStorageType storageType = ProjectDocumentStorageType.fromFileUrl(fileUrl);

        if (storageType == ProjectDocumentStorageType.OBS_DIRECT) {
            // OBS 直传文件：委托 ObsShareUrlSigner 生成预签名 URL（不校验 creatorId，对所有有项目访问权的角色放行），
            // Controller 返回 302 让浏览器直连 OBS，省后端带宽。修复 obs-direct: 前缀下载 404。
            String redirectUrl = obsShareUrlSigner.trySign(fileUrl)
                    .orElseThrow(() -> ResourceNotFoundException.withMessage(
                            "Project document file not found: " + documentId));
            return new ProjectDocumentDownloadFile(fileName, null, null, null, redirectUrl);
        }

        // 本地存储（bid-agent:// / doc-insight://）：走既有 fileStorage.load 流式下载
        LoadedProjectDocumentFile loaded = fileStorage.load(fileUrl)
                .orElseThrow(() -> ResourceNotFoundException.withMessage(
                        "Project document file not found: " + documentId));
        byte[] content = loaded.content() == null ? new byte[0] : loaded.content();
        String contentType = defaultString(loaded.contentType(),
                resolveContentType(document.getFileType(), fileName));
        long length = content.length;
        return new ProjectDocumentDownloadFile(
                fileName,
                contentType,
                length,
                loaded.resource() == null ? new ByteArrayResource(content) : loaded.resource(),
                null
        );
    }

    private void assertBidDocumentDownloadable(Long projectId, ProjectDocument document) {
        if (!BidReadinessPolicy.BID_DOCUMENT_CATEGORY.equals(document.getDocumentCategory())) {
            return;
        }
        ProjectStage stage = projectStageService.currentStage(projectId);
        // CO-442: DRAFTING（含 submit-review 子状态）和 CLOSED（结项后知识库积累）允许下载；
        // 中间阶段（EVALUATING/RESULT_PENDING/RETROSPECTIVE）只读不可下载，防止标书扩散。
        if (stage != ProjectStage.DRAFTING && stage != ProjectStage.CLOSED) {
            // 409 Conflict：与 ProjectStageService 阶段非法跳转的语义对齐。
            throw new BusinessException(409,
                    "投标文件已进入「" + stage.getDisplayName() + "」阶段，文件只读不可下载");
        }
    }

    private String resolveFileName(ProjectDocument document) {
        String name = trimToNull(document.getName());
        if (name == null) {
            String extension = extensionOf(document.getFileType());
            name = "项目文档" + extension;
        }
        return name;
    }

    private String resolveContentType(String fileType, String fileName) {
        String normalized = trimToNull(fileType);
        if (normalized != null && normalized.contains("/")) {
            try {
                return MediaType.parseMediaType(normalized).toString();
            } catch (InvalidMediaTypeException e) {
                // 非法 MIME 类型回退为二进制流
                return MediaType.APPLICATION_OCTET_STREAM.toString();
            }
        }
        String candidateName = normalized == null ? fileName : "document." + normalized;
        return MediaTypeFactory.getMediaType(candidateName)
                .orElse(MediaType.APPLICATION_OCTET_STREAM)
                .toString();
    }

    private static String extensionOf(String fileType) {
        String normalized = trimToNull(fileType);
        if (normalized == null) {
            return "";
        }
        // fileType 可能是纯扩展名（"pdf" / "docx"），也可能是 MIME 类型（"application/pdf"）。
        // 统一取 slash 后的 subtype 并去掉参数，保证 MIME 场景也能得到正确扩展名。
        String ext = normalized.replaceFirst("^[^/]+/", "").replaceFirst(";.*", "").trim();
        if (ext.isEmpty() || ext.contains(" ") || ext.contains("/")) {
            return "";
        }
        return "." + ext;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String defaultString(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized != null ? normalized : fallback;
    }
}
